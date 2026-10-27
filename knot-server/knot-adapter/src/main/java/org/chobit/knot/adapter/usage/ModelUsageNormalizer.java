package org.chobit.knot.adapter.usage;

import org.chobit.knot.adapter.model.usage.AudioBillingUsage;
import org.chobit.knot.adapter.model.usage.BillingUsage;
import org.chobit.knot.adapter.model.usage.ImageBillingUsage;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.model.usage.VideoBillingUsage;
import org.chobit.knot.adapter.usage.ModelUsage.Attributes;
import org.chobit.knot.adapter.usage.ModelUsage.Input;
import org.chobit.knot.adapter.usage.ModelUsage.InputTokens;
import org.chobit.knot.adapter.usage.ModelUsage.Output;
import org.chobit.knot.adapter.usage.ModelUsage.OutputTokens;
import org.chobit.knot.common.util.MapNumberUtils;

import java.util.Map;

/**
 * 把内部计费输入视图 {@link BillingUsage} 投影成对外统一用量 {@link ModelUsage}。
 *
 * <p>口径纪律（2026-10-23 双视图一次提取）：</p>
 * <ol>
 *   <li>两侧 token 总量、缓存读写各档<b>一律取自 {@code billing}</b>（与计费同源，不再按字段名猜语义）；</li>
 *   <li>仅「明细拆分」从原始 usage 补取：输入的 image/video token、输出的 reasoning token；</li>
 *   <li>媒体计量（图片张数、音视频秒数）取 {@code billing}，缺失时再由响应体兜底。</li>
 * </ol>
 *
 * <p>因此 {@code billing} 与 {@code usage} 天然同口径：恒有
 * {@code usage.input.tokens.total() == billing.inputTokens()}，不会出现计费按一套、展示按另一套。</p>
 *
 * <p>token 明细口径：</p>
 * <ul>
 *   <li>{@code text}：上游显式给了就用显式值，否则 {@code 该侧总量 - 其余明细}；</li>
 *   <li>{@code unclassified}：按上式配不平的残留，通常是厂商私有的新维度；</li>
 *   <li>{@code cache_write} 与 {@code cache_write_5m}/{@code cache_write_1h} 互斥由
 *       {@link InputTokens} 构造器兜平，这里无需判断。</li>
 * </ul>
 */
public final class ModelUsageNormalizer {

    private static final String DETAILS_INPUT = "input_tokens_details";
    private static final String DETAILS_PROMPT = "prompt_tokens_details";
    private static final String DETAILS_OUTPUT = "output_tokens_details";
    private static final String DETAILS_COMPLETION = "completion_tokens_details";
    private static final String CACHE_CREATION = "cache_creation";

    private ModelUsageNormalizer() {
    }

    /**
     * 唯一投影入口：由计费输入视图 + 承载 usage 的响应体产出对外用量视图。
     *
     * @param billing 内部计费输入视图，可为 {@code null}
     * @param rawBody 承载 {@code usage} 的那一层响应体（整包为响应体本身，流式为单个 SSE 事件），
     *                用于补取明细拆分与媒体计数兜底
     */
    public static ModelUsage fromBilling(BillingUsage billing, Map<String, Object> rawBody) {
        if (billing == null || billing.isEmpty()) {
            return mediaOnly(rawBody);
        }
        Map<String, Object> rawUsage = UsageRawReader.readUsage(rawBody);
        ModelUsage base = tokenModel(billing.tokenUsage(), rawUsage);
        ModelUsage withFacts = projectFacts(base, billing);
        return withBodyMedia(withFacts, rawBody, billing);
    }

    /**
     * token 视图：两侧总量与缓存取 {@code billing}，明细从原始 usage 补取。
     */
    private static ModelUsage tokenModel(TokenBillingUsage token, Map<String, Object> rawUsage) {
        if (token == null || token.isEmpty()) {
            return ModelUsage.of(Input.of(emptyInputTokens()), Output.of(emptyOutputTokens()), 0L);
        }
        long inputTotal = token.inputTokens();
        long outputTotal = token.outputTokens();
        InputTokens inputTokens;
        OutputTokens outputTokens;
        if (rawUsage != null && !rawUsage.isEmpty()) {
            inputTokens = inputTokensDetail(rawUsage, inputTotal);
            outputTokens = outputTokens(rawUsage, outputTotal);
        } else {
            inputTokens = inputTokensFromBilling(token);
            outputTokens = new OutputTokens(outputTotal, 0L, 0L, 0L, 0L);
        }
        long totalTokens = token.totalTokens() > 0 ? token.totalTokens() : inputTotal + outputTotal;
        return ModelUsage.of(Input.of(inputTokens), Output.of(outputTokens), totalTokens);
    }

    /**
     * 无原始 usage 时，输入明细只能由计费输入视图反推：text = 总量 − 缓存读写。
     */
    private static InputTokens inputTokensFromBilling(TokenBillingUsage usage) {
        long cacheWrite5m = usage.cacheWrite5mTokens();
        long cacheWrite1h = usage.cacheWrite1hTokens();
        // 与 InputTokens 构造器同一口径：ttl 明细其一大于 0 时单字段总量作废
        long cacheWrite = cacheWrite5m + cacheWrite1h > 0 ? 0L : usage.cacheWriteTokens();
        long nonTextInput = usage.cacheReadTokens() + cacheWrite + cacheWrite5m + cacheWrite1h;
        return new InputTokens(
                Math.max(0L, usage.inputTokens() - nonTextInput),
                0L,
                0L,
                usage.cacheReadTokens(),
                cacheWrite,
                cacheWrite5m,
                cacheWrite1h,
                0L
        );
    }

    /**
     * 有原始 usage 时的输入明细：总量由 {@code billing} 给定（不再用「是否上报 prompt_tokens」猜是否已含缓存），
     * 明细（缓存读写、image/video token）从原始 usage 读取，配不平的残留归 {@code unclassified}。
     */
    private static InputTokens inputTokensDetail(Map<String, Object> raw, long inputTotal) {
        long cacheWrite5m = cacheWrite5m(raw);
        long cacheWrite1h = cacheWrite1h(raw);
        long detailedCacheWrite = cacheWrite5m + cacheWrite1h;
        long cacheWrite = detailedCacheWrite > 0 ? 0L : cacheWriteTotal(raw);
        long cacheRead = flat(raw, "cache_read_input_tokens", "cache_read_tokens", "cached_read_tokens", "prompt_cache_hit_tokens")
                + nested(raw, DETAILS_PROMPT, "cached_tokens")
                + nested(raw, DETAILS_INPUT, "cached_tokens");
        long image = flat(raw, "image_tokens", "input_image_tokens")
                + nested(raw, DETAILS_INPUT, "image_tokens")
                + nested(raw, DETAILS_PROMPT, "image_tokens");
        long video = flat(raw, "video_tokens", "input_video_tokens")
                + nested(raw, DETAILS_INPUT, "video_tokens")
                + nested(raw, DETAILS_PROMPT, "video_tokens");
        long explicitText = flat(raw, "text_tokens", "input_text_tokens")
                + nested(raw, DETAILS_INPUT, "text_tokens")
                + nested(raw, DETAILS_PROMPT, "text_tokens");
        long nonText = image + video + cacheRead + cacheWrite + detailedCacheWrite;
        long text = explicitText > 0 ? explicitText : Math.max(0L, inputTotal - nonText);
        long unclassified = inputTotal > 0 ? Math.max(0L, inputTotal - text - nonText) : 0L;
        return new InputTokens(text, image, video, cacheRead, cacheWrite, cacheWrite5m, cacheWrite1h, unclassified);
    }

    private static OutputTokens outputTokens(Map<String, Object> raw, long outputTotal) {
        long image = flat(raw, "output_image_tokens")
                + nested(raw, DETAILS_OUTPUT, "image_tokens")
                + nested(raw, DETAILS_COMPLETION, "image_tokens");
        long video = flat(raw, "output_video_tokens")
                + nested(raw, DETAILS_OUTPUT, "video_tokens")
                + nested(raw, DETAILS_COMPLETION, "video_tokens");
        long reasoning = flat(raw, "reasoning_tokens", "thinking_tokens", "output_reasoning_tokens")
                + nested(raw, DETAILS_OUTPUT, "reasoning_tokens")
                + nested(raw, DETAILS_COMPLETION, "reasoning_tokens");
        long explicitText = flat(raw, "output_text_tokens")
                + nested(raw, DETAILS_OUTPUT, "text_tokens")
                + nested(raw, DETAILS_COMPLETION, "text_tokens");
        long nonText = image + video + reasoning;
        long text = explicitText > 0 ? explicitText : Math.max(0L, outputTotal - nonText);
        long unclassified = outputTotal > 0 ? Math.max(0L, outputTotal - text - nonText) : 0L;
        return new OutputTokens(text, image, video, reasoning, unclassified);
    }

    /**
     * 把计费事实投影成对外用量视图：两侧计量 + 计费属性。
     *
     * <p>⭐ 用 sealed {@code switch} 而不是 {@code instanceof} 链：新增一种
     * {@link BillingUsage} 时编译器会强制回来补投影，否则「加字段忘了投影」只会静默丢量。</p>
     */
    private static ModelUsage projectFacts(ModelUsage base, BillingUsage billing) {
        return switch (billing) {
            case TokenBillingUsage ignored -> base;
            case ImageBillingUsage image -> projectImage(base, image);
            case VideoBillingUsage video -> projectVideo(base, video);
            case AudioBillingUsage audio -> projectAudio(base, audio);
        };
    }

    private static ModelUsage projectImage(ModelUsage base, ImageBillingUsage image) {
        Output output = outputOf(base);
        return new ModelUsage(
                base.input(),
                Output.builder()
                        .tokens(output.tokens())
                        .imageCount(image.count())
                        .audioSeconds(output.audioSeconds())
                        .videoSeconds(output.videoSeconds())
                        .videoCount(output.videoCount())
                        .build(),
                Attributes.builder()
                        .width(image.width())
                        .height(image.height())
                        .pixels(image.pixels())
                        .build(),
                base.totalTokens()
        );
    }

    /**
     * VIDEO 事实投影：输入侧（张数 / 秒数）与输出侧（秒数 / 条数）都要落，
     * 否则「按输入秒 + 输入图收费」（MiniMax）与「按条收费」（Veo）在对外用量里无从体现。
     */
    private static ModelUsage projectVideo(ModelUsage base, VideoBillingUsage video) {
        Input input = inputOf(base);
        Output output = outputOf(base);
        return new ModelUsage(
                Input.builder()
                        .tokens(input.tokens())
                        .imageCount(video.inputImageCount())
                        .audioSeconds(input.audioSeconds())
                        .videoSeconds(video.inputSeconds())
                        .build(),
                Output.builder()
                        .tokens(output.tokens())
                        .imageCount(output.imageCount())
                        .audioSeconds(output.audioSeconds())
                        .videoSeconds(video.outputSeconds())
                        .videoCount(video.outputVideoCount())
                        .build(),
                Attributes.builder()
                        .resolution(video.outputResolution())
                        .aspectRatio(video.outputAspectRatio())
                        .mode(video.outputMode())
                        .sound(video.sound())
                        .voiceSpecified(video.voiceSpecified())
                        .referenceVideo(video.referenceVideo())
                        .build(),
                base.totalTokens()
        );
    }

    private static ModelUsage projectAudio(ModelUsage base, AudioBillingUsage audio) {
        Output output = outputOf(base);
        return new ModelUsage(
                base.input(),
                Output.builder()
                        .tokens(output.tokens())
                        .imageCount(output.imageCount())
                        .audioSeconds(audio.durationSeconds())
                        .videoSeconds(output.videoSeconds())
                        .videoCount(output.videoCount())
                        .build(),
                base.attributes(),
                base.totalTokens()
        );
    }

    private static Input inputOf(ModelUsage base) {
        return base.input() == null ? Input.of(emptyInputTokens()) : base.input();
    }

    private static Output outputOf(ModelUsage base) {
        return base.output() == null ? Output.of(emptyOutputTokens()) : base.output();
    }

    private static long cacheWriteTotal(Map<String, Object> raw) {
        return flat(raw, "cache_creation_input_tokens", "cache_write_input_tokens", "cache_write_tokens", "cached_write_tokens")
                + nested(raw, DETAILS_PROMPT, "cache_creation_input_tokens")
                + nested(raw, DETAILS_INPUT, "cache_creation_input_tokens");
    }

    private static long cacheWrite5m(Map<String, Object> raw) {
        return flat(raw, "cache_write_5m", "cache_creation_5m_input_tokens", "cache_write_input_tokens_5m")
                + nested(raw, CACHE_CREATION, "ephemeral_5m_input_tokens", "cache_creation_input_tokens_5m")
                + nested(raw, DETAILS_INPUT, "cache_creation_5m_input_tokens")
                + nested(raw, DETAILS_PROMPT, "cache_creation_5m_input_tokens");
    }

    private static long cacheWrite1h(Map<String, Object> raw) {
        return flat(raw, "cache_write_1h", "cache_creation_1h_input_tokens", "cache_write_input_tokens_1h")
                + nested(raw, CACHE_CREATION, "ephemeral_1h_input_tokens", "cache_creation_input_tokens_1h")
                + nested(raw, DETAILS_INPUT, "cache_creation_1h_input_tokens")
                + nested(raw, DETAILS_PROMPT, "cache_creation_1h_input_tokens");
    }

    private static InputTokens emptyInputTokens() {
        return new InputTokens(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    private static OutputTokens emptyOutputTokens() {
        return new OutputTokens(0L, 0L, 0L, 0L, 0L);
    }

    /**
     * 只有响应体、连计费输入视图都没有时用：例如图像接口只回 {@code data} 数组。
     *
     * <p>⚠ 视频不会走到这里：只要响应体里有 {@code data} / {@code videos} 或任何时长字段，
     * {@code VideoBillingUsage} 就不是空用量（条数与档位名都算量）。因此本方法把 {@code data}
     * 当图片张数是安全的——它服务的是图像类接口的兜底。</p>
     */
    public static ModelUsage mediaOnly(Map<String, Object> rawBody) {
        if (rawBody == null || rawBody.isEmpty()) {
            return null;
        }
        long imageCount = countData(rawBody);
        long videoSeconds = flat(rawBody, "duration_seconds", "video_seconds", "seconds", "duration");
        if (imageCount <= 0 && videoSeconds <= 0) {
            return null;
        }
        return ModelUsage.of(Input.of(emptyInputTokens()),
                Output.builder()
                        .tokens(emptyOutputTokens())
                        .imageCount(imageCount)
                        .videoSeconds(videoSeconds)
                        .build(),
                0L);
    }

    /**
     * 用响应体补齐「usage 里没写的多模态维度」：只在 usage 侧对应字段仍为 0 时回填。
     *
     * <p>⚠ VIDEO 一律跳过：视频响应的 {@code data} 数组装的是<b>视频</b>，回填进
     * {@code output.image_count} 会把条数错标成图片张数；而视频的各项事实已由
     * {@link #projectVideo} 从计费视图完整投影，不需要响应体兜底。</p>
     */
    private static ModelUsage withBodyMedia(ModelUsage usage, Map<String, Object> rawBody, BillingUsage billing) {
        if (usage == null || rawBody == null || rawBody.isEmpty() || billing instanceof VideoBillingUsage) {
            return usage;
        }
        Output output = usage.output();
        Output filled = output == null ? null : Output.builder()
                .tokens(output.tokens())
                .imageCount(positive(output.imageCount(), countData(rawBody)))
                .audioSeconds(positive(output.audioSeconds(), flat(rawBody, "input_audio_seconds", "audio_seconds")))
                .videoSeconds(positive(output.videoSeconds(),
                        flat(rawBody, "duration_seconds", "video_seconds", "seconds", "duration")))
                .videoCount(output.videoCount())
                .build();
        return new ModelUsage(usage.input(), filled, usage.attributes(), usage.totalTokens());
    }

    /**
     * 响应体 {@code data} 数组长度，图像类接口用它表示产出张数。
     */
    private static long countData(Map<String, Object> rawBody) {
        Object data = rawBody.get("data");
        if (data instanceof Iterable<?> iterable) {
            long count = 0L;
            for (Object ignored : iterable) {
                count++;
            }
            return count;
        }
        return 0L;
    }

    private static long positive(long current, long candidate) {
        return current > 0 ? current : Math.max(0L, candidate);
    }

    /**
     * 在同一层对象里按顺序取第一个大于 0 的数值。
     */
    private static long flat(Map<String, Object> raw, String... keys) {
        return MapNumberUtils.firstLong(raw, keys);
    }

    /**
     * 在嵌套对象里按顺序取所有大于 0 的数值之和。
     */
    private static long nested(Map<String, Object> raw, String parent, String... keys) {
        long total = 0L;
        for (String key : keys) {
            total += MapNumberUtils.nestedLong(raw, parent, key);
        }
        return total;
    }
}
