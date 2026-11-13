package org.chobit.knot.adapter.model.usage;

import org.chobit.knot.adapter.usage.extractor.UsageExtractor;

/**
 * 内部计费输入视图：把厂商各异的上游用量压成计费所需的维度，按计费模式拆分（sealed）。
 * 四类记录各承载自己的计量：{@link TokenBillingUsage}（token 各档）、{@link ImageBillingUsage}（张数 / 像素）、
 * {@link VideoBillingUsage}（时长 / 像素 / 有声 / 参考）、{@link AudioBillingUsage}（时长）。
 * 提取器 {@link UsageExtractor} 按协议产出对应类型；多模态记录内嵌 {@link TokenBillingUsage}，未上报时为空用量。
 */
public sealed interface BillingUsage
        permits TokenBillingUsage, ImageBillingUsage, VideoBillingUsage, AudioBillingUsage {

    /** 是否没有任何可用计费量（提取器与归一化入口据此判断有无用量） */
    boolean isEmpty();

    /**
     * 逐事件合并（SSE 场景）：同类型按分量取最大值；类型不同时保守返回 {@code this}
     * （同一提取器跨事件类型必然一致，分支只为防御）。
     */
    BillingUsage mergeMax(BillingUsage other);

    /**
     * 内嵌的 token 用量：token 记录返回自身，媒体记录返回内嵌部分（上游未上报时为 {@link TokenBillingUsage#empty()}）。
     * TOKEN 类规则与 token 总量统计经此取数，消费方无需逐个 sealed 类型分流。
     */
    default TokenBillingUsage tokenUsage() {
        return TokenBillingUsage.empty();
    }
}
