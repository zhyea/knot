package org.chobit.knot.adapter.model.usage;

/**
 * IMAGE 模式计费输入：出图张数 + 像素（宽 / 高）+ 请求侧输入图片张数。
 *
 * 按张计价，张数是主要计费量；{@code pixels} 是输出图片的像素总量，用作像素档位落档依据
 * （上游未上报尺寸时为 0，调用方据此走默认单价）。{@code inputImageCount} 是图生图 / 编辑请求带的输入张数，
 * 与出图张数无关；{@code tokens} 内嵌上游同时上报的 token 用量。
 */
public record ImageBillingUsage(TokenBillingUsage tokens,
                                long count,
                                long pixels,
                                long width,
                                long height,
                                long inputImageCount) implements BillingUsage {

    public ImageBillingUsage {
        tokens = tokens == null ? TokenBillingUsage.empty() : tokens;
    }

    public static ImageBillingUsage empty() {
        return new ImageBillingUsage(TokenBillingUsage.empty(), 0L, 0L, 0L, 0L, 0L);
    }

    @Override
    public TokenBillingUsage tokenUsage() {
        return tokens;
    }

    @Override
    public boolean isEmpty() {
        return tokens.isEmpty() && count <= 0 && pixels <= 0 && inputImageCount <= 0;
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
                Math.max(height, that.height),
                Math.max(inputImageCount, that.inputImageCount)
        );
    }
}
