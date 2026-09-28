package org.chobit.knot.gateway.dto.model;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

import java.util.List;

public record ModelDto(Long id, String modelCode, String name, String providerAccountCode, String providerName, String providerCode, String modelType,
                       String version, String baseUrl, String remark, boolean enabled, String logicalModelCode, Long billingRuleId, String billingRuleCode,
                       RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy,
                       List<ModelApiBindingDto> apiBindings) {
}
