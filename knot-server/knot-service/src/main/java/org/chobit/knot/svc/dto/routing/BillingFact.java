package org.chobit.knot.svc.dto.routing;

import java.math.BigDecimal;

/**
 * 计费事实：自检层（{@link BillingFactChecks}）的单一输入。
 *
 * 由 {@link BillingFactAssembler} 组装，含被测调用身份与命中规则、影子用量、影子重算账、
 * 被告账与上游 cost；自检层只比对结构内的数值，不触达生产热路径、不读 JEV。
 * 提取器回落与空用量不抛异常，改为显式标记，交给裁判层 / UI 判断是否告警。
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
