package org.chobit.knot.adapter.usage;

import org.chobit.knot.common.constants.enums.PricingPlanEnum;
import org.chobit.knot.adapter.model.usage.BillingUsage;
import org.chobit.knot.adapter.model.ref.BillingRuleRef;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 计费归一化上下文：规则 + 用量 + 原始请求 + 发生时点。
 * {@code occurredAt} 是高低峰判定的唯一时间来源，缺失时兜底为 {@link Instant#now()}。
 * {@link #pricingPlan()} 由规则的 {@code pricing_plan} 列现场解析；空值或未知编码回落 {@code FIXED}。
 */
public record NormalizedUsageContext(BillingRuleRef rule,
                                     BillingUsage usage,
                                     Map<String, Object> requestBody,
                                     Instant occurredAt) {

    public NormalizedUsageContext(BillingRuleRef rule,
                                  BillingUsage usage,
                                  Map<String, Object> requestBody,
                                  Instant occurredAt) {
        this.rule = rule;
        this.usage = usage;
        this.requestBody = requestBody == null ? Map.of() : new LinkedHashMap<>(requestBody);
        this.occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    /** 定价方案：规则列解析，空或未知回落 FIXED */
    public PricingPlanEnum pricingPlan() {
        PricingPlanEnum plan = PricingPlanEnum.fromCode(rule.pricingPlan());
        return plan == null ? PricingPlanEnum.FIXED : plan;
    }
}
