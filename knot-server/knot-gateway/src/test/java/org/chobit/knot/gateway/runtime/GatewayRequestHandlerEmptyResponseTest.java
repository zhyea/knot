package org.chobit.knot.gateway.runtime;

import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.common.model.RateLimitPolicy;
import org.chobit.knot.common.model.RetryPolicy;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.load.CaffeineModelLoadStore;
import org.chobit.knot.gateway.load.ModelLoadKeys;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.outage.OutageKeys;
import org.chobit.knot.gateway.outage.OutageTracker;
import org.chobit.knot.gateway.traffic.CaffeineTrafficCounterStore;
import org.chobit.knot.gateway.traffic.GatewayTrafficGuard;
import org.chobit.knot.gateway.traffic.TrafficCounterKeys;
import org.chobit.knot.gateway.traffic.TrafficPolicySource;
import org.chobit.knot.gateway.upstream.UpstreamProxyClient;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.svc.model.TrafficPolicies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import reactor.core.scheduler.Schedulers;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 空响应的<b>终态语义</b>：不重试、不 failover、在途计数必须归零。
 *
 * <p>这一层不能只靠 {@code RetryableClassifierTest} 覆盖：那边的 {@code terminal} 判据只管「同目标重试」，
 * 而跨候选 failover 由 {@code attemptCandidates} 的 {@code onErrorResume} 独立决定——
 * 只要它返回 {@code Mono.empty()}，异常就会被吞掉并顺延下一个候选，
 * 「直接终止」变成「换个供应商再打一次」。两处必须同时生效，故在此用双候选场景验证。</p>
 *
 * <p>⚠ 第二个用例是防回归的对照：普通上游失败<b>必须</b>继续 failover，
 * 否则本次改动会把多供应商容灾整体关掉，而上面的用例依然全绿。</p>
 */
class GatewayRequestHandlerEmptyResponseTest {

    private static final String MODEL = "MODEL";
    private static final long MODEL_ID = 7L;

    private CaffeineModelLoadStore loadStore;

    @Test
    @DisplayName("空响应直接终止：不重试、不换候选，且在途计数归零")
    void emptyResponseTerminatesWithoutRetryOrFailover() {
        AtomicInteger calls = new AtomicInteger();
        RetryPolicy policy = singleAttemptPolicy();
        GatewayRequestHandler handler = handler(calls, emptyResponse());
        GatewayRequestContext context = context(List.of(target("model-a"), target("model-b")), policy);

        GatewayUpstreamException error = assertThrows(GatewayUpstreamException.class,
                () -> handler.proxy(context, new GatewayExchange(Map.of("model", MODEL))).block());

        assertEquals(ProxyErrorCodeEnum.UPSTREAM_EMPTY_RESPONSE.code(), error.code());
        assertEquals(1, calls.get(), "空响应是确定性失败：换候选只会再空一次，不得二次调用上游");
        assertEquals(0L, loadStore.inflight(loadKeys().model("model-a")),
                "终止分支必须在返回 error 前释放在途计数——常规释放点在 switchIfEmpty，错误路径不会经过");
    }

    @Test
    @DisplayName("对照：普通上游失败仍然 failover 到下一候选")
    void ordinaryUpstreamFailureStillFailsOver() {
        AtomicInteger calls = new AtomicInteger();
        RetryPolicy policy = singleAttemptPolicy();
        GatewayRequestHandler handler = handler(calls, upstreamUnavailable());
        GatewayRequestContext context = context(List.of(target("model-a"), target("model-b")), policy);

        GatewayUpstreamException error = assertThrows(GatewayUpstreamException.class,
                () -> handler.proxy(context, new GatewayExchange(Map.of("model", MODEL))).block());

        assertEquals(503, error.httpStatus(), "最终抛出的是末次上游失败");
        assertEquals(2, calls.get(), "普通失败只关掉同目标重试，跨候选 failover 必须保留");
    }

    // ---------- 夹具 ----------

    private GatewayRequestHandler handler(AtomicInteger calls, GatewayUpstreamException failure) {
        GatewayTrafficGuard guard = new GatewayTrafficGuard(new TrafficPolicySource() {
            @Override
            public TrafficPolicies policiesOf(String resourceType, Long resourceId) {
                return new TrafficPolicies(new RateLimitPolicy(0, 1000), List.of());
            }

            @Override
            public Long providerAccountIdOf(String providerAccountCode) {
                return 99L;
            }
        }, new CaffeineTrafficCounterStore(1_000L), new TrafficCounterKeys("test", ZoneId.of("Asia/Shanghai")));
        loadStore = new CaffeineModelLoadStore(100L, 60L);
        UpstreamProxyClient proxy = new UpstreamProxyClient(null, null, null, null, null, null) {
            @Override
            public UpstreamRequestContext buildRequestContext(Map<String, Object> requestBody,
                                                             MediaType contentType,
                                                             RoutingRuleTargetDto target,
                                                             ModelApiProtocolEnum protocol,
                                                             String traceparent, String requestId) {
                // 本用例不关心上游上下文：请求处理器只把它挂在属性上供流式补记日志用
                return null;
            }

            @Override
            public ProxyResult proxy(UpstreamRequestContext upstreamContext) {
                calls.incrementAndGet();
                throw failure;
            }
        };
        return new GatewayRequestHandler(proxy, null, guard, null, null, loadStore,
                loadKeys(), null,
                new OutageTracker(null, new OutageKeys("test")), new OutageKeys("test"),
                null, "traffic-topic", null, Schedulers.immediate(), null);
    }

    private GatewayRequestContext context(List<RoutingRuleTargetDto> candidates, RetryPolicy retryPolicy) {
        GatewayRequestContext context = new GatewayRequestContext("Bearer sk", "rule-a",
                GatewayTraceContext.fromTraceparent(null), ModelApiProtocolEnum.CHAT_COMPLETIONS, null);
        context.routing(new ResolvedRouting(1L, "rule-a", 10L, "sk-routing-key", false,
                candidates, null, retryPolicy, null));
        return context;
    }

    private static ModelLoadKeys loadKeys() {
        return new ModelLoadKeys("test");
    }

    private static RoutingRuleTargetDto target(String modelCode) {
        return new RoutingRuleTargetDto(modelCode, MODEL_ID, modelCode, "upstream-model",
                "模型", "CHAT", null, 1, true, null);
    }

    private static GatewayUpstreamException emptyResponse() {
        return new GatewayUpstreamException(
                "upstream responded with status 200 but an empty body",
                ProxyErrorCodeEnum.UPSTREAM_EMPTY_RESPONSE.code());
    }

    private static GatewayUpstreamException upstreamUnavailable() {
        return new GatewayUpstreamException("upstream responded with status 503",
                ProxyErrorCodeEnum.UPSTREAM_ERROR.code(), 503, "{}");
    }

    /**
     * 单次尝试：把「同目标重试」关掉，让「是否换候选」成为唯一变量。
     */
    private static RetryPolicy singleAttemptPolicy() {
        return new RetryPolicy(Boolean.TRUE, 1, 0, 0, 1.0, Boolean.FALSE, Boolean.FALSE,
                RetryPolicy.MODE_ALLOWLIST, List.of("5xx"), RetryPolicy.MODE_SIMPLE);
    }
}
