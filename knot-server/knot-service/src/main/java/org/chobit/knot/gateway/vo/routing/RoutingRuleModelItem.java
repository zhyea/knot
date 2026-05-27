package org.chobit.knot.gateway.vo.routing;

public record RoutingRuleModelItem(
        Long modelId,
        String modelCode,
        String modelName,
        Long providerId,
        Integer priority,
        Boolean primary
) {
}
