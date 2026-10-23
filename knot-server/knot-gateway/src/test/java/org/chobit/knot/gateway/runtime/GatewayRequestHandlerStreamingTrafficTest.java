package org.chobit.knot.gateway.runtime;

import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.model.UsageAccounting;
import org.chobit.knot.gateway.traffic.CaffeineTrafficCounterStore;
import org.chobit.knot.gateway.traffic.GatewayTrafficGuard;
import org.chobit.knot.gateway.traffic.TrafficCounterKeys;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.chobit.knot.gateway.traffic.TrafficPolicySource;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.chobit.knot.gateway.runtime.GatewayRequestHandler.HIT_TARGET_ATTRIBUTE;
import static org.chobit.knot.gateway.runtime.GatewayRequestHandler.TRAFFIC_CONTEXT_ATTRIBUTE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式响应终态用量补记：验证 P0-1 修复——流式请求在流写完后把定型用量打进频控/配额计数器，
 * 不再像非流式那样只调一次、而当时用量未知（记为空）导致 TPM / token 配额 / 成本配额失效。
 *
 * <p>复用 {@code GatewayTrafficGuardTest} 的真实守卫构造（匿名 {@link TrafficPolicySource} +
 * {@link CaffeineTrafficCounterStore}），handler 其余依赖全 null——{@code recordStreamingUsage}
 * 只触达 {@code trafficGuard} 与请求上下文属性，不依赖其他协作者。</p>
 */
class GatewayRequestHandlerStreamingTrafficTest {

    private static final String MODEL = "MODEL";
    private static final long MODEL_ID = 7L;

    private GatewayTrafficGuard guard;
    private GatewayRequestHandler handler;

    private void setUpWith(TrafficPolicies policies) {
        TrafficPolicySource policySource = new TrafficPolicySource() {
            @Override
            public TrafficPolicies policiesOf(String resourceType, Long resourceId) {
                return policies;
            }

            @Override
            public Long providerAccountIdOf(String providerAccountCode) {
                return 99L;
            }
        };
        guard = new GatewayTrafficGuard(policySource, new CaffeineTrafficCounterStore(1_000L),
                new TrafficCounterKeys("test", ZoneId.of("Asia/Shanghai")));
        // 构造器第 3 个参数是 trafficGuard；其余依赖本测试路径不触达，传 null
        handler = new GatewayRequestHandler(null, null, guard, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private RoutingRuleTargetDto target() {
        return new RoutingRuleTargetDto(MODEL, MODEL_ID, "model-code", "upstream-model",
                "模型", "CHAT", null, 1, true, null);
    }

    private UsageAccounting accounting(long tokens) {
        return UsageAccounting.of(Map.of("total_tokens", tokens),
                null, null, null, new NormalizedUsage(tokens, null, null, "v1", List.of()));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("流式终态用量被补记进 TPM 计数器：累计超上限后拦截")
    void streamingUsageIsAccountedIntoCounter() {
        setUpWith(new TrafficPolicies(new RateLimitPolicy(0, 1000), List.of()));
        GatewayRequestContext context = new GatewayRequestContext("Bearer sk", "rule-a", null, null, null);
        RoutingRuleTargetDto candidate = target();
        var trafficContext = guard.newContext();
        context.putAttribute(HIT_TARGET_ATTRIBUTE, candidate);
        context.putAttribute(TRAFFIC_CONTEXT_ATTRIBUTE, trafficContext);
        ResolvedRouting routing = new ResolvedRouting(1L, "rule-a", 10L, "sk-routing-key",
                false, List.of(), null, null, null);

        handler.recordStreamingUsage(context, routing, accounting(600L));
        assertTrue(guard.checkTarget(candidate, trafficContext).allowed(),
                "第一次补记 600 < TPM 1000 应放行");

        handler.recordStreamingUsage(context, routing, accounting(600L));
        assertFalse(guard.checkTarget(candidate, trafficContext).allowed(),
                "第二次补记累计 1200 > TPM 1000 应被拦截——证明流式用量确实进了账");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("终端用量为空时短路：既不记账也不抛异常")
    void nullAccountingSkipsRecording() {
        setUpWith(new TrafficPolicies(new RateLimitPolicy(0, 1000), List.of()));
        GatewayRequestContext context = new GatewayRequestContext("Bearer sk", "rule-a", null, null, null);
        RoutingRuleTargetDto candidate = target();
        var trafficContext = guard.newContext();
        context.putAttribute(HIT_TARGET_ATTRIBUTE, candidate);
        context.putAttribute(TRAFFIC_CONTEXT_ATTRIBUTE, trafficContext);
        ResolvedRouting routing = new ResolvedRouting(1L, "rule-a", 10L, "sk-routing-key",
                false, List.of(), null, null, null);

        handler.recordStreamingUsage(context, routing, null);

        assertTrue(guard.checkTarget(candidate, trafficContext).allowed(),
                "空用量不应记账，计数器保持 0");
    }
}
