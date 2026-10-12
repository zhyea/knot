package org.chobit.knot.gateway.model;

/**
 * VIDEO 模式计费输入：时长 + 像素（宽 / 高）+ 是否有声音 + 是否有参考。
 *
 * <p>时长（秒）是主要计费量（按秒计价）；像素支撑按分辨率计价；
 * {@code sound} / {@code reference} 是请求侧生成属性（如 {@code generate_audio}、
 * {@code input_reference}），供带这些维度的规则取用，上游未体现时为 {@code false}。</p>
 *
 * <p>{@code tokens} 内嵌上游可能同时上报的 token 用量，TOKEN 类规则走它计费；
 * 纯媒体响应为空用量。</p>
 */
public record VideoBillingUsage(TokenBillingUsage tokens,
                                long durationSeconds,
                                long pixels,
                                long width,
                                long height,
                                boolean sound,
                                boolean reference) implements BillingUsage {

    public VideoBillingUsage {
        tokens = tokens == null ? TokenBillingUsage.empty() : tokens;
    }

    public static VideoBillingUsage empty() {
        return new VideoBillingUsage(TokenBillingUsage.empty(), 0L, 0L, 0L, 0L, false, false);
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
                Math.max(pixels, that.pixels),
                Math.max(width, that.width),
                Math.max(height, that.height),
                sound || that.sound,
                reference || that.reference
        );
    }
}
