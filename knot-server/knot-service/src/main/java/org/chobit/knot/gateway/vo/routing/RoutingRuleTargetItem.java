package org.chobit.knot.gateway.vo.routing;

public record RoutingRuleTargetItem(
        String targetType,
        Long targetId,
        String targetCode,
        String targetName,
        String modelType,
        Long providerId,
        Integer priority,
        Boolean primary
) {
}
