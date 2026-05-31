package org.chobit.knot.gateway.model;

import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;

import java.util.List;

public record ResolvedRouting(Long ruleId,
                              String ruleCode,
                              Long consumerId,
                              String secretKey,
                              boolean returnUsageDetail,
                              AppContext appContext,
                              RoutingRuleTargetDto resolvedModel,
                              List<RoutingRuleTargetDto> candidateModels,
                              GatewayRoutingInfo routingInfo) {
}
