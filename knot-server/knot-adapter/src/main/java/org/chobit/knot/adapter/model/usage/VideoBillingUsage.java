package org.chobit.knot.adapter.model.usage;

/**
 * VIDEO 模式计费输入：输出时长 + 输入时长 + 输入图片张数 + 像素（宽 / 高）+ 生成属性。
 *
 * <p>{@code durationSeconds}（输出秒）是主计费量；{@code inputDurationSeconds} 与
 * {@code imageCount} 支撑「合成量」与「多分量求和」两种业界口径（2026-10-25）：</p>
 * <ul>
 *   <li>合成量（Kling）：把输入秒按公式折进输出秒，仍只有一个分量、一个单价；</li>
 *   <li>多分量（MiniMax）：输出秒 / 输入秒 / 输入图片张各自计价后求和。</li>
 * </ul>
 *
 * <p>像素支撑按分辨率计价；{@code sound} / {@code reference} 是请求侧生成属性
 * （如 {@code generate_audio}、{@code input_reference}），既是<b>维度分档键</b>的组成部分
 * （豆包「分辨率 × 是否含视频输入」定价），也是<b>附加费触发条件</b>。</p>
 *
 * <p>{@code tokens} 内嵌上游可能同时上报的 token 用量，TOKEN 类规则走它计费；
 * 纯媒体响应为空用量。</p>
 */
public record VideoBillingUsage(TokenBillingUsage tokens,
                                long durationSeconds,
                                long inputDurationSeconds,
                                long imageCount,
                                long pixels,
                                long width,
                                long height,
                                boolean sound,
                                boolean reference) implements BillingUsage {

    public VideoBillingUsage {
        tokens = tokens == null ? TokenBillingUsage.empty() : tokens;
    }

    public static VideoBillingUsage empty() {
        return new VideoBillingUsage(TokenBillingUsage.empty(), 0L, 0L, 0L, 0L, 0L, 0L, false, false);
    }

    @Override
    public TokenBillingUsage tokenUsage() {
        return tokens;
    }

    @Override
    public boolean isEmpty() {
        return tokens.isEmpty() && durationSeconds <= 0 && pixels <= 0;
    }

    @Override
    public VideoBillingUsage mergeMax(BillingUsage other) {
        if (!(other instanceof VideoBillingUsage that)) {
            return this;
        }
        return new VideoBillingUsage(
                tokens.mergeMax(that.tokens()),
                Math.max(durationSeconds, that.durationSeconds),
                Math.max(inputDurationSeconds, that.inputDurationSeconds),
                Math.max(imageCount, that.imageCount),
                Math.max(pixels, that.pixels),
                Math.max(width, that.width),
                Math.max(height, that.height),
                sound || that.sound,
                reference || that.reference
        );
    }
}
