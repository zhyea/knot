package org.chobit.knot.gateway.dto.routing;

import java.math.BigDecimal;
import java.util.List;

/**
 * 计费事实：自检层（{@link BillingFactChecks}）的单一输入。
 *
 * <p>由 {@link BillingFactAssembler} 组装，包含四类信息：
 * <ul>
 *   <li>{@code subject} / {@code ruleRef}：被测调用身份与命中规则（被告链依据）；</li>
 *   <li>{@code usage}：影子提取的用量（未计价）；</li>
 *   <li>{@code shadowAmount}：同口径重算账（离线复算，与生产同逻辑）；</li>
 *   <li>{@code gatewayBilling}：被告账（网关实际注入的 knot_extend.billing）；</li>
 *   <li>{@code upstreamCost}：上游返回的 cost（可空 → 触发 SKIP）。</li>
 * </ul>
 *
 * <p>自检层只比对结构内的数值，不触达生产热路径，也不读 JEV。</p>
 *
 * <p><b>影子链不抛异常，但回落必须可见（2026-10-23 计费去兜底 Phase 4）</b>：
 * 本结构是<b>离线复算</b>，语义是「尽力算出来给你对照」，不是生产计费闸门——
 * 配置错误不该让路由测试抽屉直接报错、让用户看不到任何对照结果。
 * 因此提取器回落与空用量不抛异常，改为在这里显式标记，交给裁判层/UI 判断是否告警。</p>
 *
 * @param subject           主体
 * @param usage             影子用量视图
 * @param ruleRef           规则快照
 * @param shadowAmount      影子重算账（可空：未命中规则）
 * @param gatewayBilling    被告账（可空：响应未返回 knot_extend.billing）
 * @param upstreamCost      上游 cost（可空：上游未返回）
 * @param extractorFallback 是否回落到默认提取器（编码缺失或未知）→ 配置错误信号
 * @param usageAbsent       提取结果为空（用量缺失）→ 影子账不可信信号
 */
public record BillingFact(BillingFactSubject subject,
                          BillingFactUsage usage,
                          BillingFactRuleRef ruleRef,
                          BillingFactAmount shadowAmount,
                          BillingFactAmount gatewayBilling,
                          BigDecimal upstreamCost,
                          boolean extractorFallback,
                          boolean usageAbsent) {

    /**
     * 便利构造：无标记场景（生产链路直装、测试桩）。
     */
    public BillingFact(BillingFactSubject subject,
                       BillingFactUsage usage,
                       BillingFactRuleRef ruleRef,
                       BillingFactAmount shadowAmount,
                       BillingFactAmount gatewayBilling,
                       BigDecimal upstreamCost) {
        this(subject, usage, ruleRef, shadowAmount, gatewayBilling, upstreamCost, false, false);
    }
}
