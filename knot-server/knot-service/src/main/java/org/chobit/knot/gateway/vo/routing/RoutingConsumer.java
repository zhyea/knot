package org.chobit.knot.gateway.vo.routing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

public record RoutingConsumer(
        Long id,
        @Size(max = 32) String consumerCode,
        @NotBlank String name,
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
