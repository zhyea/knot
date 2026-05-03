package org.chobit.knot.gateway.vo.routing;

public record RoutingTestResult(Long matchedRuleId, Long targetProviderId, Long targetModelId, String status) {
}
