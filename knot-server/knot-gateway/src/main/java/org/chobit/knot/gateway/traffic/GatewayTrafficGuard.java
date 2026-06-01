package org.chobit.knot.gateway.traffic;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.chobit.knot.gateway.service.GatewayDataService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class GatewayTrafficGuard {

    private static final String SECOND_WINDOW = "SECOND";
    private static final String MINUTE_WINDOW = "MINUTE";
    private static final long SECOND_MILLIS = 1_000L;
    private static final long MINUTE_MILLIS = 60_000L;

    private final GatewayDataService dataService;
    private final ConcurrentHashMap<RateKey, WindowCounter> rateCounters = new ConcurrentHashMap<>();

    /**
     * Creates per-request traffic check state.
     */
    public TrafficCheckContext newContext() {
        return new TrafficCheckContext();
    }

    /**
     * Checks traffic policies that are fixed for a single gateway request.
     */
    public boolean checkRouting(ResolvedRouting routing) {
        return checkResource(TrafficResourceTypeEnum.APP.code(), routing.routingInfo().app().id())
                && checkResource(TrafficResourceTypeEnum.ROUTING_RULE.code(), routing.ruleId())
                && checkResource(TrafficResourceTypeEnum.ROUTING_CONSUMER.code(), routing.consumerId());
    }

    /**
     * Checks traffic policies for the current routing target.
     */
    public boolean checkTarget(RoutingRuleTargetDto target, TrafficCheckContext context) {
        return checkResource(TrafficResourceTypeEnum.MODEL.code(), target.targetId())
                && checkProvider(target.providerId(), context);
    }

    private boolean checkProvider(Long providerId, TrafficCheckContext context) {
        if (providerId == null) {
            return true;
        }
        return context.providerResults.computeIfAbsent(providerId,
                id -> checkResource(TrafficResourceTypeEnum.PROVIDER.code(), id));
    }

    private boolean checkResource(String resourceType, Long resourceId) {
        if (resourceId == null) {
            return true;
        }
        TrafficPolicies policies = dataService.loadTrafficPolicies(resourceType, resourceId);
        return checkRateLimit(resourceType, resourceId, policies)
                && checkQuota(resourceType, resourceId, policies);
    }

    private boolean checkRateLimit(String resourceType, Long resourceId, TrafficPolicies policies) {
        if (policies == null || policies.rateLimitPolicy() == null) {
            return true;
        }
        RateLimitPolicy policy = policies.rateLimitPolicy();
        return checkWindow(resourceType, resourceId, SECOND_WINDOW, SECOND_MILLIS, policy.perSecond())
                && checkWindow(resourceType, resourceId, MINUTE_WINDOW, MINUTE_MILLIS, policy.perMinute());
    }

    private boolean checkQuota(String resourceType, Long resourceId, TrafficPolicies policies) {
        if (policies == null || policies.quotaPolicy() == null) {
            return true;
        }
        // Usage counters can be plugged in here.
        return true;
    }

    private boolean checkWindow(String resourceType,
                                Long resourceId,
                                String windowType,
                                long windowMillis,
                                int limit) {
        if (limit <= 0) {
            return true;
        }
        RateKey key = new RateKey(resourceType, resourceId, windowType);
        return rateCounters.computeIfAbsent(key, ignored -> new WindowCounter())
                .allow(limit, System.currentTimeMillis() / windowMillis);
    }

    private record RateKey(String resourceType, Long resourceId, String windowType) {
    }

    public static final class TrafficCheckContext {
        private final Map<Long, Boolean> providerResults = new HashMap<>();
    }

    private static final class WindowCounter {
        private long window;
        private int count;

        private synchronized boolean allow(int limit, long currentWindow) {
            if (window != currentWindow) {
                window = currentWindow;
                count = 0;
            }
            if (count >= limit) {
                return false;
            }
            count++;
            return true;
        }
    }
}
