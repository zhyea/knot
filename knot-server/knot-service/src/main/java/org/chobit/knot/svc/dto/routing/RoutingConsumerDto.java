package org.chobit.knot.svc.dto.routing;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.util.Sensitive;

import java.util.List;

public record RoutingConsumerDto(
        Long id,
        String consumerCode,
        String name,
        String userUsername,
        String userName,
        @Sensitive String secretKey,
        boolean returnUsageDetail,
        boolean enabled,
        Long ruleCount,
        RateLimitPolicy rateLimitPolicy,
        List<QuotaPolicy> quotaPolicies
) {
}
