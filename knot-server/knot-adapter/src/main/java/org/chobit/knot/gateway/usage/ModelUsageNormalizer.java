package org.chobit.knot.gateway.usage;

import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.model.usage.ModelUsage;
import org.chobit.knot.gateway.model.usage.ModelUsage.Input;
import org.chobit.knot.gateway.model.usage.ModelUsage.InputTokens;
import org.chobit.knot.gateway.model.usage.ModelUsage.Output;
import org.chobit.knot.gateway.model.usage.ModelUsage.OutputTokens;
import org.chobit.knot.gateway.util.MapNumberUtils;

import java.util.Map;

/**
 * 把厂商各异的上游用量归一到 {@link ModelUsage}。
 *
 * <p>归一化顺序：
 * <ol>
 *   <li>有原始 {@code usage} 对象 → {@link #fromRaw(Map)}；</li>
 *   <li>多模态维度（图片张数、音视频秒数）再从响应体补一次，因为多数厂商不把它写进 usage；</li>
 *   <li>完全没有原始 usage → 退回内部计费输入视图 {@link BillingUsage} 推断。</li>
 * </ol>
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
     * 归一化入口：原始 usage 优先，响应体补多模态维度，最后退到计费输入视图。
     *
     * @param rawUsage 上游原始 {@code usage} 对象，可为 {@code null}
     * @param rawBody  承载 usage 的那一层响应体，可为 {@code null}
     * @param billing  内部计费输入视图，可为 {@code null}
     */
    public static ModelUsage fromSource(Map<String, Object> rawUsage,
                                        Map<String, Object> rawBody,
                                        BillingUsage billing) {
        if (rawUsage != null && !rawUsage.isEmpty()) {
            ModelUsage usage = fromRaw(rawUsage);
            if (usage != null) {
                return withBodyMedia(usage, rawBody);
            }
        }
        return fromBilling(billing, rawBody);
    }

    /**
     * 纯 usage 对象归一化：各家字段名差异在这一层收敛。
     */
    public static ModelUsage fromRaw(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        long inputTotal = flat(raw, AiPayloadFields.INPUT_TOKENS, AiPayloadFields.PROMPT_TOKENS);
        long outputTotal = flat(raw, AiPayloadFields.OUTPUT_TOKENS, AiPayloadFields.COMPLETION_TOKENS);

        InputTokens inputTokens = inputTokens(raw, inputTotal);
        OutputTokens outputTokens = outputTokens(raw, outputTotal);

        Input input = Input.of(
                inputTokens,
                flat(raw, "input_image_count", "image_count", "images", "num_images"),
                flat(raw, "input_audio_seconds", "audio_seconds", "prompt_audio_seconds"),
                flat(raw, "input_video_seconds", "video_seconds", "prompt_video_seconds")
        );
        Output output = Output.of(
                outputTokens,
                flat(raw, "output_image_count", "completion_image_count"),
                flat(raw, "output_audio_seconds", "completion_audio_seconds"),
                flat(raw, "output_video_seconds", "completion_video_seconds", "duration_seconds")
        );

        long totalTokens = flat(raw, AiPayloadFields.TOTAL_TOKENS);
        if (totalTokens <= 0) {
            totalTokens = inputTokens.total() + outputTokens.total();
        }
        if (totalTokens <= 0 && input == null && output == null) {
            return null;
        }
        return ModelUsage.of(input, output, totalTokens);
    }

    /**
     * 无原始 usage 时的兜底：用内部计费输入视图重建，多模态维度按响应体归因。
     */
    public static ModelUsage fromBilling(BillingUsage usage, Map<String, Object> rawBody) {
        if (usage == null || usage.isEmpty()) {
            return mediaOnly(rawBody);
        }
        InputTokens inputTokens = new InputTokens(
                // BillingUsage.inputTokens 语义为「输入侧总量」：OpenAI 的 prompt_tokens 含缓存命中，
                // Anthropic 侧提取器已把 cache_read/write 加回总量，两种语义下
                // 「纯文本 = 总量 − 缓存读写」都成立；负数说明语义已乱，兜底为 0。
                Math.max(0L, usage.inputTokens() - usage.cacheReadTokens() - usage.cacheWriteTokens()),
                0L,
                0L,
                usage.cacheReadTokens(),
                usage.cacheWriteTokens(),
                0L,
                0L,
                0L
        );
        OutputTokens outputTokens = new OutputTokens(usage.outputTokens(), 0L, 0L, 0L, 0L);
        Input input = Input.of(inputTokens);
        Output output = Output.of(outputTokens);
        ModelUsage normalized = ModelUsage.of(input, output, usage.totalTokens());
        return withBodyMedia(normalized, rawBody);
    }

    /**
     * 只有响应体、连计费输入视图都没有时用：例如图像接口只回 {@code data} 数组。
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
        return ModelUsage.of(Input.of(new InputTokens(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L)),
                Output.of(new OutputTokens(0L, 0L, 0L, 0L, 0L), imageCount, 0L, videoSeconds),
                0L);
    }

    /**
     * 用响应体补齐「usage 里没写的多模态维度」：只在 usage 侧对应字段仍为 0 时回填。
     */
    private static ModelUsage withBodyMedia(ModelUsage usage, Map<String, Object> rawBody) {
        if (usage == null || rawBody == null || rawBody.isEmpty()) {
            return usage;
        }
        Output output = usage.output();
        Output filled = output == null ? null : Output.of(
                output.tokens(),
                positive(output.imageCount(), countData(rawBody)),
                positive(output.audioSeconds(), flat(rawBody, "input_audio_seconds", "audio_seconds")),
                positive(output.videoSeconds(), flat(rawBody, "duration_seconds", "video_seconds", "seconds", "duration"))
        );
        return ModelUsage.of(usage.input(), filled, usage.totalTokens());
    }

    private static InputTokens inputTokens(Map<String, Object> raw, long inputTotal) {
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
