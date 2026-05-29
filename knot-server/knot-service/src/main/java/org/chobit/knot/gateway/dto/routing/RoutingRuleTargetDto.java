package org.chobit.knot.gateway.dto.routing;

public record RoutingRuleTargetDto(
        String targetType,
        Long targetId,
        String targetCode,
        String targetName,
        String modelType,
        Long providerId,
        int priority,
        boolean primary
) {
}
