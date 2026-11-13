package org.chobit.knot.adapter.model.usage;

import org.chobit.knot.adapter.usage.ModelUsage;

import java.util.Map;

/**
 * 一次上游调用的用量账目，贯穿「提取 → 计费 → 输出」三段。
 * {@code billing} 与 {@code usage} 同为提取器在同一协议上下文下产出的双视图，输出层直接消费。
 *
 * @param rawUsage   上游原始 {@code usage} 对象，上游未上报时为 {@code null}
 * @param rawBody    承载 {@code rawUsage} 的那一层响应体（整包为响应体本身，流式为单个 SSE 事件），
 *                   仅用于审计与 {@code upstreamUsage} 透出
 * @param billing    内部计费输入视图
 * @param usage      对外统一用量视图，可为 {@code null}
 * @param normalized 计费结果视图
 */
public record UsageAccounting(Map<String, Object> rawUsage,
                              Map<String, Object> rawBody,
                              BillingUsage billing,
                              ModelUsage usage,
                              NormalizedUsage normalized) {

    public static UsageAccounting empty() {
        return new UsageAccounting(null, null, TokenBillingUsage.empty(), null, null);
    }

    /**
     * 降级账目：用量提取失败且配置允许放行时使用；计费输入与计费结果为空，即这笔请求不计费。
     * 与 {@link #empty()} 的差别是保留 {@code rawUsage} / {@code rawBody}，供事后重算与对账。
     */
    public static UsageAccounting degraded(Map<String, Object> rawUsage, Map<String, Object> rawBody) {
        return new UsageAccounting(rawUsage, rawBody, TokenBillingUsage.empty(), null, null);
    }

    public static UsageAccounting of(Map<String, Object> rawUsage,
                                     Map<String, Object> rawBody,
                                     BillingUsage billing,
                                     ModelUsage usage,
                                     NormalizedUsage normalized) {
        return new UsageAccounting(rawUsage, rawBody, billing, usage, normalized);
    }

    /**
     * 是否没有任何可用用量：计费输入为空、且没有计费结果与用完视图。
     */
    public boolean isEmpty() {
        return (billing == null || billing.isEmpty()) && normalized == null && usage == null;
    }

    /**
     * 计费结果中的 token 总量（输入 + 输出），供限额与配额记账换算用量。
     * 优先取计费结果，无计费结果时退回内部计费输入的 token 总量；媒体用量经内嵌 token 部分取数（纯媒体响应为 0）。
     */
    public long totalTokens() {
        if (normalized != null && normalized.totalTokens() != null && normalized.totalTokens() > 0) {
            return normalized.totalTokens();
        }
        if (billing != null) {
            TokenBillingUsage token = billing.tokenUsage();
            long total = token.totalTokens();
            if (total <= 0) {
                total = token.inputTokens() + token.outputTokens();
            }
            return Math.max(0L, total);
        }
        return 0L;
    }
}
