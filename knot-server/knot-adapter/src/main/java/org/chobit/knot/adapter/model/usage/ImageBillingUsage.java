package org.chobit.knot.adapter.model.usage;

/**
 * IMAGE 模式计费输入：张数 + 像素（宽 / 高）。
 *
 * <p>张数是主要计费量（按张计价）；像素支撑按像素/按分辨率计价，
 * 宽高为结构化来源（上游未上报时为 0，{@code pixels} 同步为 0）。</p>
 *
 * <p>{@code tokens} 内嵌上游可能同时上报的 token 用量（如 gpt-image-1），
 * TOKEN 类规则走它计费；纯媒体响应为空用量。</p>
 */
public record ImageBillingUsage(TokenBillingUsage tokens,
                                long count,
                                long pixels,
                                long width,
                                long height) implements BillingUsage {

    public ImageBillingUsage {
        tokens = tokens == null ? TokenBillingUsage.empty() : tokens;
    }

    public static ImageBillingUsage empty() {
        return new ImageBillingUsage(TokenBillingUsage.empty(), 0L, 0L, 0L, 0L);
    }

    @Override
    public TokenBillingUsage tokenUsage() {
        return tokens;
    }

    @Override
    public boolean isEmpty() {
        return tokens.isEmpty() && count <= 0 && pixels <= 0;
    }

    @Override
    public ImageBillingUsage mergeMax(BillingUsage other) {
        if (!(other instanceof ImageBillingUsage that)) {
            return this;
        }
        return new ImageBillingUsage(
                tokens.mergeMax(that.tokens()),
                Math.max(count, that.count),
                Math.max(pixels, that.pixels),
                Math.max(width, that.width),
                Math.max(height, that.height)
        );
    }
}
