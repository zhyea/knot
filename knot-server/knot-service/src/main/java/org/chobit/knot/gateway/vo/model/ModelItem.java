package org.chobit.knot.gateway.vo.model;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

public record ModelItem(Long id, String name, Long providerId, String modelType, String version, boolean enabled,
                        RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}
