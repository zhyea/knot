package org.chobit.knot.adapter.usage;

import lombok.Builder;
import org.chobit.knot.adapter.model.usage.NormalizedUsage;
import org.chobit.knot.adapter.model.usage.BillingUsage;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 统一用量结构体 {@code knot_extend.usage}：上游 {@code usage} 的<b>归一化</b>，外加本次请求的计费属性。
 *
 * <p>按「输入侧 / 输出侧」两段组织：每侧自带 token 明细，非 token 维度
 * （图片张数、音频秒数、视频秒数、视频条数）与 token 明细平级，避免多类用量挤进同一个字段。</p>
 *
 * <h3>它为什么必须承载计费属性</h3>
 * <p>调用方与数仓要能<b>只凭这一份用量自己算钱</b>（转卖 token、成本分摊、毛利核算都需要），
 * 而分辨率、画幅、模式档、有无声音这些是价目表的分档键——只给量不给档位，下游无法复算。
 * 网关不提供计费规则给下游，所以档位必须在用量里。价格本身仍只在 {@link NormalizedUsage}。</p>
 *
 * <p>⚠ 但档位<b>不是上游 usage 报的</b>，而是本次请求声明的（上游从不回传分辨率）。
 * 因此它们单独放 {@link Attributes} 一段，不与两侧计量混排——结构上就能看出
 * 「哪些是上游说的、哪些是我们按请求体声明的」。</p>
 *
 * <h3>字段语义约束</h3>
 * <ol>
 *   <li>所有计数均为非负整数；{@code 0} 值与空串在 JSON 输出时被省略
 *       （{@link JsonInclude.Include#NON_DEFAULT}），与外部 {@code omitempty} 语义一致。</li>
 *   <li><b>缺席即默认</b>：下游读到「没有 {@code attributes.resolution}」应等价于
 *       「未按分辨率分档」，与网关取价时回落默认档是同一口径。</li>
 *   <li>{@code cache_write} 与 {@code cache_write_5m} / {@code cache_write_1h} 互斥：
 *       5m / 1h 是写缓存的细分口径，只要明细其一大于 0，总量 {@code cache_write} 即作废归 0。
 *       该约束由 {@link InputTokens} 的紧凑构造器强制执行，调用方无需自行判断。</li>
 *   <li>{@code text} 指纯文本 token；{@code unclassified} 指上游给了该侧 token 总量、
 *       但明细无法解释干净的残留。归一化后恒有
 *       {@code text + image + video + cache_read + cache_write(或 5m+1h) + unclassified = 该侧 token 总量}。</li>
 *   <li>{@code total_tokens} 优先沿用上游上报值；上游未上报时由两侧 token 总量相加补齐。</li>
 * </ol>
 *
 * <p>本结构<b>不含价格</b>。计费仍由 {@link BillingUsage}（事实）+ {@link NormalizedUsage}（价格）承载，
 * 三者在 {@link KnotExtendPayload} 与 Kafka 流量消息里并列输出。
 * 本类型是 {@code BillingUsage} 的<b>无损投影</b>：加计费事实字段必须两边同改，
 * 由 {@code ModelUsageNormalizerTest} 的字段对齐用例守门。</p>
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record ModelUsage(Input input,
                         Output output,
                         Attributes attributes,
                         @JsonProperty("total_tokens") long totalTokens) {

    public ModelUsage {
        attributes = attributes == null || attributes.isEmpty() ? null : attributes;
        totalTokens = Math.max(0L, totalTokens);
    }

    /**
     * 构造用量归一结果，{@code totalTokens} 由两侧 token 总量相加得出。
     */
    public static ModelUsage of(Input input, Output output) {
        return new ModelUsage(input, output, null, tokenTotal(input) + tokenTotal(output));
    }

    /**
     * 构造用量归一结果并显式指定 {@code total_tokens}（上游已上报总量时使用）。
     */
    public static ModelUsage of(Input input, Output output, long totalTokens) {
        return new ModelUsage(input, output, null, totalTokens);
    }

    /**
     * 逐事件合并：token 明细取总量更大的一整份（明细必须自洽，不能跨事件按字段拼），
     * 媒体计量取 max，计费属性取先出现的非空值 / 布尔取或。
     *
     * <p>与 {@code BillingUsage#mergeMax} 同规则——两个视图必须由同一套合并语义推出，
     * 否则流式下会出现「计费按合并后的量、展示按某一个事件的量」的分叉。</p>
     */
    public ModelUsage mergeMax(ModelUsage other) {
        if (other == null) {
            return this;
        }
        return new ModelUsage(
                Input.merge(input, other.input),
                Output.merge(output, other.output),
                mergeAttributes(attributes, other.attributes),
                Math.max(totalTokens, other.totalTokens)
        );
    }

    private static Attributes mergeAttributes(Attributes current, Attributes other) {
        if (current == null) {
            return other;
        }
        return other == null ? current : current.mergeMax(other);
    }

    /**
     * 输入侧用量。
     *
     * @param tokens       输入 token 明细
     * @param imageCount   输入图片张数（图生视频的首帧图 / 参考图）
     * @param audioSeconds 输入音频秒数
     * @param videoSeconds 输入视频秒数（参考视频 / 视频编辑的输入时长）
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @Builder
    public record Input(InputTokens tokens,
                        @JsonProperty("image_count") long imageCount,
                        @JsonProperty("audio_seconds") long audioSeconds,
                        @JsonProperty("video_seconds") long videoSeconds) {

        public static Input of(InputTokens tokens) {
            return Input.builder().tokens(tokens).build();
        }

        public long tokenTotal() {
            return tokens == null ? 0L : tokens.total();
        }

        static Input merge(Input current, Input other) {
            if (current == null) {
                return other;
            }
            if (other == null) {
                return current;
            }
            return Input.builder()
                    .tokens(largerTotal(current.tokens, other.tokens))
                    .imageCount(Math.max(current.imageCount, other.imageCount))
                    .audioSeconds(Math.max(current.audioSeconds, other.audioSeconds))
                    .videoSeconds(Math.max(current.videoSeconds, other.videoSeconds))
                    .build();
        }

        private static InputTokens largerTotal(InputTokens current, InputTokens other) {
            if (current == null) {
                return other;
            }
            return other != null && other.total() > current.total() ? other : current;
        }
    }

    /**
     * 输出侧用量。
     *
     * @param tokens       输出 token 明细
     * @param imageCount   输出图片张数
     * @param audioSeconds 输出音频秒数
     * @param videoSeconds 输出视频秒数
     * @param videoCount   输出视频条数（按条计价，如 Veo）
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @Builder
    public record Output(OutputTokens tokens,
                         @JsonProperty("image_count") long imageCount,
                         @JsonProperty("audio_seconds") long audioSeconds,
                         @JsonProperty("video_seconds") long videoSeconds,
                         @JsonProperty("video_count") long videoCount) {

        public static Output of(OutputTokens tokens) {
            return Output.builder().tokens(tokens).build();
        }

        public long tokenTotal() {
            return tokens == null ? 0L : tokens.total();
        }

        static Output merge(Output current, Output other) {
            if (current == null) {
                return other;
            }
            if (other == null) {
                return current;
            }
            return Output.builder()
                    .tokens(largerTotal(current.tokens, other.tokens))
                    .imageCount(Math.max(current.imageCount, other.imageCount))
                    .audioSeconds(Math.max(current.audioSeconds, other.audioSeconds))
                    .videoSeconds(Math.max(current.videoSeconds, other.videoSeconds))
                    .videoCount(Math.max(current.videoCount, other.videoCount))
                    .build();
        }

        private static OutputTokens largerTotal(OutputTokens current, OutputTokens other) {
            if (current == null) {
                return other;
            }
            return other != null && other.total() > current.total() ? other : current;
        }
    }

    /**
     * 本次请求的计费属性：价目表的分档键，<b>来源是请求体而非上游 usage</b>。
     *
     * <p>值一律原样保留厂商写法（{@code 768P} / {@code 16:9} / {@code wan-pro} /
     * {@code MOTION_CONTROL}），归一成键空间是下游自己的事——网关不替下游猜档位名。
     * 空串归一成 {@code null}，配合 {@code NON_DEFAULT} 在 JSON 里整项省略。</p>
     *
     * @param resolution      分辨率档位名（{@code 720P} / {@code 1080P} / {@code 4K}…）
     * @param aspectRatio     画幅（{@code 16:9} / {@code 9:16} / {@code 1:1}…）
     * @param mode            模式 / 功能档（{@code wan-std}、{@code MOTION_CONTROL}…）
     * @param width           图片宽（像素）
     * @param height          图片高（像素）
     * @param pixels          图片总像素（宽 × 高）
     * @param sound           是否有声输出
     * @param voiceSpecified  是否指定音色
     * @param referenceVideo  是否有参考 / 输入视频
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @Builder
    public record Attributes(String resolution,
                             @JsonProperty("aspect_ratio") String aspectRatio,
                             String mode,
                             long width,
                             long height,
                             long pixels,
                             boolean sound,
                             @JsonProperty("voice_specified") boolean voiceSpecified,
                             @JsonProperty("reference_video") boolean referenceVideo) {

        public Attributes {
            resolution = trimToNull(resolution);
            aspectRatio = trimToNull(aspectRatio);
            mode = trimToNull(mode);
            width = Math.max(0L, width);
            height = Math.max(0L, height);
            pixels = Math.max(0L, pixels);
        }

        public static Attributes empty() {
            return builder().build();
        }

        /** 全空即「本次请求没有可分档的属性」，由 {@link ModelUsage} 的构造器整段省略 */
        public boolean isEmpty() {
            return resolution == null && aspectRatio == null && mode == null
                    && width <= 0 && height <= 0 && pixels <= 0
                    && !sound && !voiceSpecified && !referenceVideo;
        }

        Attributes mergeMax(Attributes other) {
            if (other == null) {
                return this;
            }
            return Attributes.builder()
                    .resolution(firstText(resolution, other.resolution))
                    .aspectRatio(firstText(aspectRatio, other.aspectRatio))
                    .mode(firstText(mode, other.mode))
                    .width(Math.max(width, other.width))
                    .height(Math.max(height, other.height))
                    .pixels(Math.max(pixels, other.pixels))
                    .sound(sound || other.sound)
                    .voiceSpecified(voiceSpecified || other.voiceSpecified)
                    .referenceVideo(referenceVideo || other.referenceVideo)
                    .build();
        }

        private static String firstText(String current, String other) {
            return current != null ? current : other;
        }

        private static String trimToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }

    /**
     * 输入侧 token 明细。
     *
     * <p>{@code cacheWrite} 与 {@code cacheWrite5m} / {@code cacheWrite1h} 互斥，
     * 明细优先：任一明细大于 0 时 {@code cacheWrite} 被强制归 0。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public record InputTokens(long text,
                              long image,
                              long video,
                              @JsonProperty("cache_read") long cacheRead,
                              @JsonProperty("cache_write") long cacheWrite,
                              @JsonProperty("cache_write_5m") long cacheWrite5m,
                              @JsonProperty("cache_write_1h") long cacheWrite1h,
                              long unclassified) {

        public InputTokens {
            if (cacheWrite5m > 0 || cacheWrite1h > 0) {
                cacheWrite = 0L;
            }
        }

        /**
         * 明细项之和，含 {@code unclassified}。
         */
        public long total() {
            return text + image + video + cacheRead + cacheWrite + cacheWrite5m + cacheWrite1h + unclassified;
        }

        /**
         * 除 {@code text} 与 {@code unclassified} 之外的明细之和，用于推算纯文本 token。
         */
        public long nonTextTotal() {
            return image + video + cacheRead + cacheWrite + cacheWrite5m + cacheWrite1h;
        }
    }

    /**
     * 输出侧 token 明细，{@code reasoning} 为思考 token。
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public record OutputTokens(long text,
                               long image,
                               long video,
                               long reasoning,
                               long unclassified) {

        public long total() {
            return text + image + video + reasoning + unclassified;
        }

        public long nonTextTotal() {
            return image + video + reasoning;
        }
    }

    private static long tokenTotal(Input input) {
        return input == null ? 0L : input.tokenTotal();
    }

    private static long tokenTotal(Output output) {
        return output == null ? 0L : output.tokenTotal();
    }
}
