package org.chobit.knot.gateway.dto.routing;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

public record RoutingConsumerDto(
        Long id,
        String consumerCode,
        String name,
        Long userId,
        String userName,
        String secretKey,
        boolean returnUsageDetail,
        boolean enabled,
        Long ruleCount,
        RateLimitPolicy rateLimitPolicy,
        QuotaPolicy quotaPolicy
) {
}
