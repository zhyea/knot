package org.chobit.knot.adapter.usage;

import lombok.Builder;
import org.chobit.knot.adapter.model.usage.NormalizedUsage;
import org.chobit.knot.adapter.model.usage.BillingUsage;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 统一用量结构体 {@code knot_extend.usage}：上游 {@code usage} 的归一化，外加本次请求的计费属性；按「输入侧 / 输出侧」两段组织。
 * 非 token 维度（图片张数、音频秒数、视频秒数、视频条数）与 token 明细平级；本结构不含价格。
 * 计数均为非负整数，{@code 0} 与空串在 JSON 输出时省略（{@link JsonInclude.Include#NON_DEFAULT}），缺席即默认档。
 * {@code cache_write} 与 {@code cache_write_5m} / {@code cache_write_1h} 互斥，任一明细大于 0 时由 {@link InputTokens} 的紧凑构造器把总量归 0。
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
     * 本次请求的计费属性：价目表的分档键，来源是请求体而非上游 usage。
     * 值一律原样保留厂商写法（{@code 768P} / {@code 16:9} / {@code wan-pro} / {@code MOTION_CONTROL}）。
     * 空串归一成 {@code null}，配合 {@code NON_DEFAULT} 整项省略。
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
     * 输入侧 token 明细。{@code cacheWrite} 与 {@code cacheWrite5m} / {@code cacheWrite1h} 互斥，任一明细大于 0 时 {@code cacheWrite} 被强制归 0。
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
