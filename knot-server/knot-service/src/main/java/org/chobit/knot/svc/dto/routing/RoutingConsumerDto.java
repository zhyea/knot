package org.chobit.knot.svc.dto.routing;

import org.chobit.knot.common.model.QuotaPolicy;
import org.chobit.knot.common.model.RateLimitPolicy;
import org.chobit.knot.common.util.Sensitive;

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
