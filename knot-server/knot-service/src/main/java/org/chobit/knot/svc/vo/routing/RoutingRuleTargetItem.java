package org.chobit.knot.svc.vo.routing;

public record RoutingRuleTargetItem(
        String targetType,
        Long targetId,
        String targetCode,
        String targetName,
        String modelType,
        String providerAccountCode,
        Integer priority,
        Boolean primary
) {
}
