package org.chobit.knot.gateway.traffic;

import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayTrafficGuardTest {

    private static final String MODEL = "MODEL";
    private static final long MODEL_ID = 7L;

    private GatewayTrafficGuard guard;

    @Test
    void allowsWhenNoPolicyConfigured() {
        setUpWith(new TrafficPolicies(null, null));

        assertTrue(guard.checkTarget(target(), guard.newContext()).allowed());
    }

    @Test
    void rateLimitRejectsRequestBeyondPerSecond() {
        setUpWith(new TrafficPolicies(new RateLimitPolicy(2, 0, "SECOND"), null));
        var context = guard.newContext();

        assertTrue(guard.checkTarget(target(), context).allowed());
        assertTrue(guard.checkTarget(target(), context).allowed());

        TrafficDecision rejected = guard.checkTarget(target(), context);
        assertFalse(rejected.allowed());
        assertEquals(TrafficRejectReason.RATE_LIMIT, rejected.reason());
        assertEquals(2L, rejected.limit());
        assertEquals(3L, rejected.used());
    }

    @Test
    void dailyQuotaRejectsAfterRecordedRequests() {
        setUpWith(new TrafficPolicies(null, new QuotaPolicy(2L, 0L, 0L, false)));
        var context = guard.newContext();

        assertTrue(guard.checkTarget(target(), context).allowed());
        guard.record(null, target(), 0L, context);
        assertTrue(guard.checkTarget(target(), context).allowed());
        guard.record(null, target(), 0L, context);

        TrafficDecision rejected = guard.checkTarget(target(), context);
        assertFalse(rejected.allowed());
        assertEquals(TrafficRejectReason.QUOTA_DAILY, rejected.reason());
        assertEquals(2L, rejected.used());
    }

    @Test
    void tokenQuotaAccumulatesUntilLimit() {
        setUpWith(new TrafficPolicies(null, new QuotaPolicy(0L, 0L, 100L, false)));
        var context = guard.newContext();

        guard.record(null, target(), 60L, context);
        assertTrue(guard.checkTarget(target(), context).allowed());
        guard.record(null, target(), 60L, context);

        TrafficDecision rejected = guard.checkTarget(target(), context);
        assertFalse(rejected.allowed());
        assertEquals(TrafficRejectReason.QUOTA_TOKEN, rejected.reason());
        assertEquals(100L, rejected.limit());
        assertEquals(120L, rejected.used());
    }

    private void setUpWith(TrafficPolicies policies) {
        TrafficPolicySource policySource = new TrafficPolicySource() {

            @Override
            public TrafficPolicies policiesOf(String resourceType, Long resourceId) {
                return policies;
            }

            @Override
            public Long providerAccountIdOf(String providerAccountCode) {
                return null;
            }
        };
        guard = new GatewayTrafficGuard(policySource, new CaffeineTrafficCounterStore(1_000L),
                new TrafficCounterKeys("test", ZoneId.of("Asia/Shanghai")));
    }

    /**
     * 未绑定供应商账户，因此只校验模型维度。
     */
    private RoutingRuleTargetDto target() {
        return new RoutingRuleTargetDto(MODEL, MODEL_ID, "model-code", "upstream-model",
                "模型", "CHAT", null, 1, true);
    }
}
