package org.chobit.knot.gateway.vo.model;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

public record ModelItem(Long id, String modelCode, String name, Long providerId, String providerName, String modelType,
                        String version, boolean enabled, Long logicalModelId, Long billingRuleId, String billingRuleName,
                        RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}
