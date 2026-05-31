package org.chobit.knot.gateway.service;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GatewayTrafficGuard {

    private final GatewayDataCache dataCache;

    /**
     * Checks whether the requested condition is satisfied. Executes the public operation.
     */
    public boolean check(ResolvedRouting routing, RoutingRuleTargetDto target) {
        return checkResource(TrafficResourceTypeEnum.ROUTING_CONSUMER.code(), routing.consumerId())
                && checkResource(TrafficResourceTypeEnum.APP.code(), routing.appContext().id())
                && checkResource(TrafficResourceTypeEnum.ROUTING_RULE.code(), routing.ruleId())
                && checkResource(TrafficResourceTypeEnum.MODEL.code(), target.targetId());
    }

    private boolean checkResource(String resourceType, Long resourceId) {
        if (resourceId == null) {
            return true;
        }
        TrafficPolicies ignored = dataCache.loadTrafficPolicies(resourceType, resourceId);
        // Real-time counters can be plugged in here. Loading policies here keeps all gateway traffic checks explicit.
        return true;
    }
}
