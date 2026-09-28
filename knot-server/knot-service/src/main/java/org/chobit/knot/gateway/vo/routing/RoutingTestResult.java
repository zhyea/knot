package org.chobit.knot.gateway.vo.routing;

public record RoutingTestResult(
        Long matchedRuleId,
        String targetProviderAccountCode,
        Long targetModelId,
        String modelCode,
        String protocol,
        String status,
        String curl,
        Integer httpStatus,
        String responseBody,
        String errorMessage
) {
}
