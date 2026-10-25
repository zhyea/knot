package org.chobit.knot.svc.vo.routing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

import java.util.List;

public record RoutingConsumer(
        Long id,
        @Size(max = 32) String consumerCode,
        @NotBlank String name,
        String userUsername,
        String userName,
        String secretKey,
        boolean returnUsageDetail,
        boolean enabled,
        Long ruleCount,
        RateLimitPolicy rateLimitPolicy,
        @Valid List<QuotaPolicy> quotaPolicies
) {
}
