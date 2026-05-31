package org.chobit.knot.gateway.model;

public record AppContext(Long id,
                         String appId,
                         String name,
                         RateLimitPolicy rateLimitPolicy,
                         QuotaPolicy quotaPolicy) {
}
