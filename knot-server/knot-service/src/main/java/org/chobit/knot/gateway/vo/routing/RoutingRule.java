package org.chobit.knot.gateway.vo.routing;

public record RoutingRule(Long id, String name, String strategy, String conditionExpr,
                          Long targetProviderId, Long targetModelId, int priority, boolean enabled) {
}
