package org.chobit.knot.adapter.model.usage;

import org.chobit.knot.adapter.usage.extractor.UsageExtractor;

/**
 * 内部计费输入视图：把厂商各异的上游用量压成计费所需的维度。
 *
 * <p><b>按计费模式拆分（sealed）</b>，每类计量各归各的记录，不再用单个 {@code amount}
 * 字段混装张数/秒数/次数：
 * <ul>
 *   <li>{@link TokenBillingUsage} —— TOKEN 模式：input/output/total 与缓存读写各档 token；</li>
 *   <li>{@link ImageBillingUsage} —— IMAGE 模式：张数、像素（宽/高）；</li>
 *   <li>{@link VideoBillingUsage} —— VIDEO 模式：时长、像素（宽/高）、是否有声音、是否有参考；</li>
 *   <li>{@link AudioBillingUsage} —— AUDIO 模式：时长。</li>
 * </ul>
 *
 * <p>提取器 {@code UsageExtractor<T extends BillingUsage>} 按协议产出对应类型，
 * 计算器按 sealed 类型分摄取量，类型即契约，见 {@link UsageExtractor}。</p>
 *
 * <p><b>多模态记录内嵌 {@link TokenBillingUsage}</b>：上游可能同时上报 token 用量
 * （如 gpt-image-1 的 {@code usage.total_tokens}），内嵌后 TOKEN 类规则仍能按 token 计费，
 * 不丢量；纯媒体响应（无 usage）时内嵌为空用量。</p>
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
     * 内嵌的 token 用量：token 记录返回自身，媒体记录返回其内嵌部分
     * （上游未上报时为 {@link TokenBillingUsage#empty()}）。
     *
     * <p>TOKEN 类规则与 token 总量统计经此取数，消费方无需逐个 sealed 类型分流。</p>
     */
    default TokenBillingUsage tokenUsage() {
        return TokenBillingUsage.empty();
    }
}
