package org.chobit.knot.gateway.usage;

import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.model.BillingUsage;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 计费归一化上下文：规则 + 用量 + 原始请求 + **发生时点**。
 *
 * <p>{@code occurredAt} 是高低峰判定的唯一时间来源，一路透传到方案层做峰谷判定。
 * 首期由网关取 {@link Instant#now()}（进程 UTC），后续若要改成请求入场时间，只需改注入方，
 * 下游自动跟随。
 *
 * <p>缺失时兜底为 {@code Instant.now()}：宁可用近似时间让峰谷生效，也不因 missing 让整条链退化。
 */
public record NormalizedUsageContext(BillingRuleEntity rule,
                                     BillingUsage usage,
                                     Map<String, Object> requestBody,
                                     Instant occurredAt) {

    public NormalizedUsageContext(BillingRuleEntity rule,
                                  BillingUsage usage,
                                  Map<String, Object> requestBody,
                                  Instant occurredAt) {
        this.rule = rule;
        this.usage = usage;
        this.requestBody = requestBody == null ? Map.of() : new LinkedHashMap<>(requestBody);
        this.occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}
