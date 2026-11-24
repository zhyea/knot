package org.chobit.knot.adapter.model.usage;

import org.chobit.knot.common.model.BillingConfig;

import java.math.BigDecimal;

/**
 * 图片按 token 计价时的明细行类型标识：每条明细描述一类模态 token 用量的计费结果。
 * {@link #key()} 即对外序列化字符串，下游对账 / 报表按其聚合，不允许随意改名。
 *
 * <p>模态与单价的对应关系只在本枚举声明：明细行、试算展示都从这里取价，
 * 缓存分项未配置时回落同模态输入价的规则由 {@link BillingConfig.ImageTokenPrices} 唯一定义。</p>
 */
public enum ImageTokenDetailKind {

    /** 未命中缓存的文本输入 token */
    TEXT_INPUT("inputText"),
    /** 未命中缓存的图像输入 token */
    IMAGE_INPUT("inputImage"),
    /** 命中缓存的文本输入 token */
    CACHED_TEXT_INPUT("cachedInputText"),
    /** 命中缓存的图像输入 token */
    CACHED_IMAGE_INPUT("cachedInputImage"),
    /** 图像输出 token */
    IMAGE_OUTPUT("outputImage");

    private final String key;

    ImageTokenDetailKind(String key) {
        this.key = key;
    }

    /** 对外序列化用的明细类型字符串 */
    public String key() {
        return key;
    }

    /**
     * 该模态在给定模态价表下对应的单价，未配置时为 {@code null}。
     * 缓存分项读 {@code cacheReadText} / {@code cacheReadImage}，留空回落同模态输入价。
     */
    public BigDecimal unitPriceIn(BillingConfig.ImageTokenPrices prices) {
        if (prices == null) {
            return null;
        }
        return switch (this) {
            case TEXT_INPUT -> prices.inputText();
            case IMAGE_INPUT -> prices.inputImage();
            case CACHED_TEXT_INPUT -> prices.cachedTextPrice();
            case CACHED_IMAGE_INPUT -> prices.cachedImagePrice();
            case IMAGE_OUTPUT -> prices.outputImage();
        };
    }
}
