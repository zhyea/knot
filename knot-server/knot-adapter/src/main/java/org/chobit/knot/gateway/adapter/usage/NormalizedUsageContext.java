package org.chobit.knot.gateway.adapter.usage;

import org.chobit.knot.gateway.constants.enums.PricingPlanEnum;
import org.chobit.knot.gateway.adapter.model.usage.BillingUsage;
import org.chobit.knot.gateway.adapter.model.ref.BillingRuleRef;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 计费归一化上下文：规则 + 用量 + 原始请求 + **发生时点**。
 *
 * <p>{@code occurredAt} 是高低峰判定的唯一时间来源，一路透传到方案层做峰谷判定。
 * 首期由网关取 {@link Instant#now()}（进程 UTC），后续若要改成请求入场时间，只需改注入方，
 * 下游自动跟随。缺失时兜底为 {@code Instant.now()}：宁可用近似时间让峰谷生效，
 * 也不因 missing 让整条链退化。
 *
 * <p>{@link #pricingPlan()} 是「价」层方案：由规则的 {@code pricing_plan} 列现场解析，
 * 计算器按方案分发到对应计费方法，不再各自解析规则列；空值或未知编码回落 {@code FIXED}，
 * 与 {@code BillingConfig} 构建方案领域对象时 null 视为 FIXED 的口径一致。
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
