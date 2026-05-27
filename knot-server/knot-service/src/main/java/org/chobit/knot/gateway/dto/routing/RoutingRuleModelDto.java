package org.chobit.knot.gateway.dto.routing;

public record RoutingRuleModelDto(
        Long modelId,
        String modelCode,
        String modelName,
        Long providerId,
        int priority,
        boolean primary
) {
}
