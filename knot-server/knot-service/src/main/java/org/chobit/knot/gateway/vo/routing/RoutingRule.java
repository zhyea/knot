package org.chobit.knot.gateway.vo.routing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

import java.util.List;

public record RoutingRule(
        Long id,
        @Size(max = 32) String ruleCode,
        @NotBlank String name,
        @Size(max = 128) String appScenario,
        List<@Size(max = 64) String> modelTypes,
        List<Long> consumerIds,
        List<String> consumerNames,
        Long appId,
        String appName,
        Long userId,
        String userName,
        @NotBlank String strategy,
        boolean enabled,
        @Valid List<RoutingRuleModelItem> models,
        RateLimitPolicy rateLimitPolicy,
        QuotaPolicy quotaPolicy
) {
}
