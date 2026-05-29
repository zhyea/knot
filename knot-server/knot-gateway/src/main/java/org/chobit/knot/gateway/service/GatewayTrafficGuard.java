package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.TrafficResourceType;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.springframework.stereotype.Service;

@Service
public class GatewayTrafficGuard {

    private final ResourceTrafficPolicySupport trafficPolicySupport;

    public GatewayTrafficGuard(ResourceTrafficPolicySupport trafficPolicySupport) {
        this.trafficPolicySupport = trafficPolicySupport;
    }

    public boolean check(RoutingAuthService.ResolvedRouting routing, RoutingRuleTargetDto target) {
        return checkResource(TrafficResourceType.ROUTING_CONSUMER, routing.consumerId())
                && checkResource(TrafficResourceType.APP, routing.appContext().id())
                && checkResource(TrafficResourceType.ROUTING_RULE, routing.ruleId())
                && checkResource(TrafficResourceType.MODEL, target.targetId());
    }

    private boolean checkResource(String resourceType, Long resourceId) {
        if (resourceId == null) {
            return true;
        }
        ResourceTrafficPolicySupport.TrafficPolicies ignored = trafficPolicySupport.load(resourceType, resourceId);
        // Real-time counters can be plugged in here. Loading policies here keeps all gateway traffic checks explicit.
        return true;
    }
}
