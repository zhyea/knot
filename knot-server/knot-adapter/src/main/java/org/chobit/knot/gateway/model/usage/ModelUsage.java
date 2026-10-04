package org.chobit.knot.gateway.model.usage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 统一用量结构体 {@code knot_extend.usage}。
 *
 * <p>按「输入侧 / 输出侧」两段组织：每侧自带 token 明细，非 token 维度
 * （图片张数、音频秒数、视频秒数）与 token 明细平级，避免多类用量挤进同一个字段。
 *
 * <h3>字段语义约束</h3>
 * <ol>
 *   <li>所有计数均为非负整数；{@code 0} 值在 JSON 输出时被省略
 *       （{@link JsonInclude.Include#NON_DEFAULT}），与外部 {@code omitempty} 语义一致。</li>
 *   <li>{@code cache_write} 与 {@code cache_write_5m} / {@code cache_write_1h} 互斥：
 *       5m / 1h 是写缓存的细分口径，只要明细其一大于 0，总量 {@code cache_write} 即作废归 0。
 *       该约束由 {@link InputTokens} 的紧凑构造器强制执行，调用方无需自行判断。</li>
 *   <li>{@code text} 指纯文本 token；{@code unclassified} 指上游给了该侧 token 总量、
 *       但明细无法解释干净的残留。归一化后恒有
 *       {@code text + image + video + cache_read + cache_write(或 5m+1h) + unclassified = 该侧 token 总量}。</li>
 *   <li>{@code total_tokens} 优先沿用上游上报值；上游未上报时由两侧 token 总量相加补齐。</li>
 * </ol>
 *
 * <p>本结构只描述「用量」，不参与定价。计费仍由
 * {@link org.chobit.knot.gateway.model.BillingUsage} + {@link org.chobit.knot.gateway.model.NormalizedUsage}
 * 承载，两者在 {@link KnotExtendPayload} 中并列输出。</p>
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record ModelUsage(Input input,
                         Output output,
                         @JsonProperty("total_tokens") long totalTokens) {

    /**
     * 构造两侧齐全的用量归一结果，{@code totalTokens} 由两侧 token 总量相加得出。
     */
    public static ModelUsage of(Input input, Output output) {
        return new ModelUsage(input, output, tokenTotal(input) + tokenTotal(output));
    }

    /**
     * 构造用量归一结果并显式指定 {@code total_tokens}（上游已上报总量时使用）。
     */
    public static ModelUsage of(Input input, Output output, long totalTokens) {
        return new ModelUsage(input, output, Math.max(0L, totalTokens));
    }

    /**
     * 输入侧用量。
     *
     * @param tokens       输入 token 明细
     * @param imageCount   输入图片张数
     * @param audioSeconds 输入音频秒数
     * @param videoSeconds 输入视频秒数
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public record Input(InputTokens tokens,
                        @JsonProperty("image_count") long imageCount,
                        @JsonProperty("audio_seconds") long audioSeconds,
                        @JsonProperty("video_seconds") long videoSeconds) {

        public static Input of(InputTokens tokens) {
            return new Input(tokens, 0L, 0L, 0L);
        }

        public static Input of(InputTokens tokens, long imageCount, long audioSeconds, long videoSeconds) {
            return new Input(tokens, imageCount, audioSeconds, videoSeconds);
        }

        public long tokenTotal() {
            return tokens == null ? 0L : tokens.total();
        }
    }

    /**
     * 输出侧用量。
     *
     * @param tokens       输出 token 明细
     * @param imageCount   输出图片张数
     * @param audioSeconds 输出音频秒数
     * @param videoSeconds 输出视频秒数
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public record Output(OutputTokens tokens,
                         @JsonProperty("image_count") long imageCount,
                         @JsonProperty("audio_seconds") long audioSeconds,
                         @JsonProperty("video_seconds") long videoSeconds) {

        public static Output of(OutputTokens tokens) {
            return new Output(tokens, 0L, 0L, 0L);
        }

        public static Output of(OutputTokens tokens, long imageCount, long audioSeconds, long videoSeconds) {
            return new Output(tokens, imageCount, audioSeconds, videoSeconds);
        }

        public long tokenTotal() {
            return tokens == null ? 0L : tokens.total();
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
