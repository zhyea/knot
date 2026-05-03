package com.knot.gateway.dto.routing;

public record RoutingRuleDto(Long id, String name, String strategy, String conditionExpr,
                             Long targetProviderId, Long targetModelId, int priority, boolean enabled) {
}
