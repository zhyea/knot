package org.chobit.knot.gateway.dto.routing;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

import java.util.List;

public record RoutingRuleDto(
        Long id,
        String ruleCode,
        String name,
        String appScenario,
        List<String> modelTypes,
        List<Long> consumerIds,
        List<String> consumerNames,
        Long appId,
        String appName,
        Long userId,
        String userName,
        String strategy,
        boolean enabled,
        List<RoutingRuleModelDto> models,
        RateLimitPolicy rateLimitPolicy,
        QuotaPolicy quotaPolicy
) {
}
