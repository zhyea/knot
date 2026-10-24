package org.chobit.knot.gateway.model;

import org.chobit.knot.gateway.model.usage.ModelUsage;

import java.util.Map;

/**
 * 一次上游调用的用量账目，贯穿「提取 → 计费 → 输出」三段。
 *
 * <p>分层说明：
 * <ul>
 *   <li>{@code rawUsage} / {@code rawBody}：上游原文，只做透传与审计，不参与任何计算；</li>
 *   <li>{@code billing}：内部计费输入视图（扁平），由各 {@code UsageExtractor} 按厂商规则产出；</li>
 *   <li>{@code usage}：对外统一用量视图，由提取器在同一份协议上下文下产出，与 {@code billing} 同口径；</li>
 *   <li>{@code normalized}：按计费规则算出的金额视图，网关对外输出用。</li>
 * </ul>
 *
 * <p>{@code usage} 与 {@code billing} 同为提取器的双视图产物（2026-10-23 方案）：输出层直接消费，
 * 不再二次解析原始 usage。</p>
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
     * 降级账目：用量提取失败、且配置允许放行时使用（零账 + 保留上游原文证据）。
     *
     * <p>与 {@link #empty()} 的差别只在<b>保留 {@code rawUsage} / {@code rawBody}</b>——
     * 计费输入与计费结果一律为空，即这笔请求不计费。上游原文是事后重算（影子链 / 对账）的唯一
     * 依据，降级不能把它一起丢掉，否则「零账」就变成「无从追查的零账」。</p>
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
     * 计费结果中的 token 总量，用于流式逐事件挑选「信息量最大」的一份用量。
     *
     * <p>优先取计费结果；无计费结果（未命中计费规则）时退回内部计费输入的 token 总量，
     * 否则逐事件比较会全部退化成 0，只能靠「取最后一个」碰运气。
     * 媒体用量经内嵌 token 部分取数（纯媒体响应为 0）。</p>
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
