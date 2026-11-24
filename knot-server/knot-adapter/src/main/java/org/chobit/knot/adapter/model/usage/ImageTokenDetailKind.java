package org.chobit.knot.adapter.model.usage;

/**
 * 图片按 token 计价时的明细行类型标识：每条明细描述一类模态 token 用量的计费结果。
 * {@link #key()} 即对外序列化字符串，下游对账 / 报表按其聚合，不允许随意改名。
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
}
