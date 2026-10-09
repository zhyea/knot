package org.chobit.knot.gateway.vo.model;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

import java.util.List;

public record ModelItem(Long id, String modelCode, String upstreamModel, String providerAccountCode, String providerName, String providerCode, String modelType,
                        String version, String baseUrl, String remark, boolean enabled, String logicalModelCode, String billingRuleCode,
                        RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy,
                        List<ModelApiBindingItem> apiBindings,
                        boolean deleted) {
}
