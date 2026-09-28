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
     */
    public long totalTokens() {
        return normalized == null || normalized.totalTokens() == null ? 0L : normalized.totalTokens();
    }
}
