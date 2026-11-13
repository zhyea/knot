package org.chobit.knot.adapter.model.usage;

import lombok.Builder;

/**
 * VIDEO 模式计费输入：输出量（时长 / 条数 / 档位）+ 输入量（时长 / 张数）+ 生成属性（有声 / 音色 / 参考视频）。
 * 时长以毫秒存储，计费与展示统一经 {@link #outputSeconds()} 向上取整到秒；{@code outputVideoCount} 为 0 表示上游未报，取价时经 {@link #billedVideoCount()} 兜成 1 条。
 * 分辨率 / 画幅 / 模式原样保存厂商档位名（空串表示未声明、取价回落默认档），键空间归一等价由 Calculator 做；{@code sound} / {@code referenceVideo} 参与维度分档键与附加费。
 * {@code voiceSpecified} 与 {@code sound} 正交；{@code tokens} 内嵌上游可能同时上报的 token 用量，纯媒体响应为空用量。
 */
@Builder
public record VideoBillingUsage(TokenBillingUsage tokens,
                                long outputVideoMillis,
                                String outputResolution,
                                String outputAspectRatio,
                                String outputMode,
                                long outputVideoCount,
                                long inputVideoMillis,
                                long inputImageCount,
                                boolean sound,
                                boolean voiceSpecified,
                                boolean referenceVideo) implements BillingUsage {

    /** 毫秒 → 秒的进位基数：按秒向上取整，不截断 */
    private static final long MILLIS_PER_SECOND = 1_000L;

    public VideoBillingUsage {
        tokens = tokens == null ? TokenBillingUsage.empty() : tokens;
        outputResolution = text(outputResolution);
        outputAspectRatio = text(outputAspectRatio);
        outputMode = text(outputMode);
        outputVideoMillis = Math.max(outputVideoMillis, 0L);
        outputVideoCount = Math.max(outputVideoCount, 0L);
        inputVideoMillis = Math.max(inputVideoMillis, 0L);
        inputImageCount = Math.max(inputImageCount, 0L);
    }

    public static VideoBillingUsage empty() {
        return builder().build();
    }

    @Override
    public TokenBillingUsage tokenUsage() {
        return tokens;
    }

    /** 输出时长（秒，向上取整）：计费与展示的唯一口径。 */
    public long outputSeconds() {
        return ceilSeconds(outputVideoMillis);
    }

    /** 输入时长（秒，向上取整） */
    public long inputSeconds() {
        return ceilSeconds(inputVideoMillis);
    }

    /**
     * 计费条数：按条计价（Veo）的乘数；{@code outputVideoCount} 为 0 表示上游未上报，按 1 条兜底。
     */
    public long billedVideoCount() {
        return outputVideoCount <= 0 ? 1L : outputVideoCount;
    }

    /**
     * 有无输入视频的唯一判据（全仓共用）：{@code inputVideoMillis > 0} 或 {@code referenceVideo} 非空，两者取或。
     */
    public boolean hasInputVideo() {
        return inputVideoMillis > 0 || referenceVideo;
    }

    @Override
    public boolean isEmpty() {
        // 只认「量」：token / 秒数 / 档位名 / 条数。属性（画幅、模式、有声、音色、参考）一律不参与，
        // 否则「请求声明了属性但响应没给量」的提交态回执会被判成有量，A2 的 MISMATCH 语义失效。
        return tokens.isEmpty()
                && outputVideoMillis <= 0
                && outputResolution.isBlank()
                && outputVideoCount <= 0;
    }

    @Override
    public VideoBillingUsage mergeMax(BillingUsage other) {
        if (!(other instanceof VideoBillingUsage that)) {
            return this;
        }
        return new VideoBillingUsage(
                tokens.mergeMax(that.tokens()),
                Math.max(outputVideoMillis, that.outputVideoMillis),
                // 字符串取先出现的非空值：同一请求的多事件必然一致，空值只可能来自缺字段的事件
                firstText(outputResolution, that.outputResolution),
                firstText(outputAspectRatio, that.outputAspectRatio),
                firstText(outputMode, that.outputMode),
                Math.max(outputVideoCount, that.outputVideoCount),
                Math.max(inputVideoMillis, that.inputVideoMillis),
                Math.max(inputImageCount, that.inputImageCount),
                sound || that.sound,
                voiceSpecified || that.voiceSpecified,
                referenceVideo || that.referenceVideo
        );
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstText(String current, String other) {
        return current.isBlank() ? other : current;
    }

    private static long ceilSeconds(long millis) {
        return millis <= 0 ? 0L : (millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND;
    }
}
