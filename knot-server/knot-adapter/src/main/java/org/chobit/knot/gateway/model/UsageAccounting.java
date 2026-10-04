package org.chobit.knot.gateway.model;

import java.util.Map;

/**
 * 一次上游调用的用量账目，贯穿「提取 → 计费 → 输出」三段。
 *
 * <p>分层说明：
 * <ul>
 *   <li>{@code rawUsage} / {@code rawBody}：上游原文，只做透传与审计，不参与任何计算；</li>
 *   <li>{@code billing}：内部计费输入视图（扁平），由各 {@code UsageExtractor} 按厂商规则产出；</li>
 *   <li>{@code normalized}：按计费规则算出的金额视图，网关对外输出用。</li>
 * </ul>
 *
 * @param rawUsage 上游原始 {@code usage} 对象，上游未上报时为 {@code null}
 * @param rawBody  承载 {@code rawUsage} 的那一层响应体（整包为响应体本身，流式为单个 SSE 事件），
 *                 用于多模态维度（图片张数、音视频秒数）在 usage 缺失时的兜底推断
 * @param billing  内部计费输入视图
 * @param normalized 计费结果视图
 */
public record UsageAccounting(Map<String, Object> rawUsage,
                              Map<String, Object> rawBody,
                              BillingUsage billing,
                              NormalizedUsage normalized) {

    public static UsageAccounting empty() {
        return new UsageAccounting(null, null, BillingUsage.empty(), null);
    }

    public static UsageAccounting of(Map<String, Object> rawUsage,
                                     Map<String, Object> rawBody,
                                     BillingUsage billing,
                                     NormalizedUsage normalized) {
        return new UsageAccounting(rawUsage, rawBody, billing, normalized);
    }

    /**
     * 是否没有任何可用用量：计费输入为空且没有计费结果。
     */
    public boolean isEmpty() {
        return (billing == null || billing.isEmpty()) && normalized == null;
    }

    /**
     * 计费结果中的 token 总量，用于流式逐事件挑选「信息量最大」的一份用量。
     *
     * <p>优先取计费结果；无计费结果（未命中计费规则）时退回内部计费输入的 token 总量，
     * 否则逐事件比较会全部退化成 0，只能靠「取最后一个」碰运气。</p>
     */
    public long totalTokens() {
        if (normalized != null && normalized.totalTokens() != null && normalized.totalTokens() > 0) {
            return normalized.totalTokens();
        }
        if (billing != null) {
            long total = billing.totalTokens();
            if (total <= 0) {
                total = billing.inputTokens() + billing.outputTokens();
            }
            return Math.max(0L, total);
        }
        return 0L;
    }
}
