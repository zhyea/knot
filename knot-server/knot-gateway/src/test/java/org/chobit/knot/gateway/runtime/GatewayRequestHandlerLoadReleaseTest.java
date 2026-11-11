package org.chobit.knot.gateway.runtime;

import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.model.RateLimitPolicy;
import org.chobit.knot.common.model.RetryPolicy;
import org.chobit.knot.gateway.load.CaffeineModelLoadStore;
import org.chobit.knot.gateway.load.ModelLoadKeys;
import org.chobit.knot.gateway.load.ModelLoadStore;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.outage.OutageKeys;
import org.chobit.knot.gateway.outage.OutageTracker;
import org.chobit.knot.gateway.redis.RedisUnavailableException;
import org.chobit.knot.gateway.traffic.CaffeineTrafficCounterStore;
import org.chobit.knot.gateway.traffic.GatewayTrafficGuard;
import org.chobit.knot.gateway.traffic.GatewayTrafficGuard.TrafficCheckContext;
import org.chobit.knot.gateway.traffic.TrafficCounterKeys;
import org.chobit.knot.gateway.traffic.TrafficPolicySource;
import org.chobit.knot.gateway.traffic.TrafficUsage;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 在途计数的<b>释放路径</b>：候选成功之后只要还有一步抛异常，计数也必须归零。
 *
 * <p>{@code modelLoadStore.enter} 之后原本有三处释放点——确定性上游失败、非流式成功、
 * 上游失败后换候选——但它们都长在 {@code map} / {@code flatMap} / {@code switchIfEmpty} 内部。
 * 候选成功后紧跟着的记账、摘除统计、写响应头任何一步抛了非 {@code GatewayUpstreamException}
 * （最典型是 {@code trafficGuard.record} 撞上 Redis 故障），三条路径一条都不会走到，
 * 计数只增不减。{@code LEAST_LOAD} 以它选模，脏负载会让流量长期偏向错误目标，且故障恢复后不自动消除。</p>
 *
 * <p>⚠ 第二个用例是防回归对照：正常成功必须释放，且<b>恰好一次</b>。
 * 释放是 Redis 计数减法，多减一次等于凭空抹掉别的请求的在途量——
 * {@code CaffeineModelLoadStore} 有「不得压出负数」的保护，会把重复递减吃成 0，
 * 所以这里用计数桩直接数 {@code leave} 次数，别指望在途值能暴露重复释放。</p>
 */
class GatewayRequestHandlerLoadReleaseTest {

    private static final String MODEL = "MODEL";
    private static final long MODEL_ID = 7L;

    @Test
    @DisplayName("候选成功后记账抛 Redis 故障：在途仍归零，异常照常上抛")
    void redisFailureAfterSuccessStillReleasesLoad() {
        CaffeineModelLoadStore loadStore = new CaffeineModelLoadStore(100L, 60L);
        GatewayUpstreamGuardStub guard = new GatewayUpstreamGuardStub(true);
        GatewayRequestHandler handler = handler(loadStore, guard, success());
        GatewayRequestContext context = context(List.of(target("model-a"), target("model-b")));

        assertThrows(RedisUnavailableException.class,
                () -> handler.proxy(context, new GatewayExchange(Map.of("model", MODEL))).block(),
                "记账失败属于网关自身故障，必须上抛（storage_unavailable → 503），不能被 failover 吞掉");

        assertEquals(0L, loadStore.inflight(loadKeys().model("model-a")),
                "记账抛错走不到任何常规释放点，必须由 doFinally 兜底归零");
    }

    @Test
    @DisplayName("对照：正常成功只释放一次（重复递减会抹掉别人的在途量）")
    void successReleasesExactlyOnce() {
        CountingLoadStore loadStore = new CountingLoadStore();
        GatewayRequestHandler handler = handler(loadStore, new GatewayUpstreamGuardStub(false), success());
        GatewayRequestContext context = context(List.of(target("model-a")));

        handler.proxy(context, new GatewayExchange(Map.of("model", MODEL))).block();

        assertEquals(1, loadStore.enters.get(), "每个候选只 enter 一次");
        assertEquals(1, loadStore.leaves.get(), "成功路径只允许一次 leave：CAS 收敛 + doFinally 兜底不得叠加");
    }

    // ---------- 夹具 ----------

    /**
     * 记账守卫替身：{@code failOnRecord=true} 时模拟 Redis 不可用。
     *
     * <p>用子类覆写而非 Mockito——JDK 25 下 Mockito 不能 mock 具体类（00-core 已记）。</p>
     */
    private static final class GatewayUpstreamGuardStub extends GatewayTrafficGuard {

        private final boolean failOnRecord;

        private GatewayUpstreamGuardStub(boolean failOnRecord) {
            super(policySource(), new CaffeineTrafficCounterStore(1_000L),
                    new TrafficCounterKeys("test", ZoneId.of("Asia/Shanghai")));
            this.failOnRecord = failOnRecord;
        }

        @Override
        public void record(ResolvedRouting routing, RoutingRuleTargetDto target,
                           TrafficUsage usage, TrafficCheckContext context) {
            if (failOnRecord) {
                throw new RedisUnavailableException("traffic", "record", "test-key",
                        new IllegalStateException("redis down"));
            }
            super.record(routing, target, usage, context);
        }
    }

    /** 只数次数、不模拟存储语义的在途桩：暴露「重复释放」这种被 clamp 掩盖的问题。 */
    private static final class CountingLoadStore implements ModelLoadStore {

        private final Map<String, Long> values = new ConcurrentHashMap<>();
        private final AtomicInteger enters = new AtomicInteger();
        private final AtomicInteger leaves = new AtomicInteger();

        @Override
        public String id() {
            return "counting";
        }

        @Override
        public long enter(String key) {
            enters.incrementAndGet();
            return values.merge(key, 1L, Long::sum);
        }

        @Override
        public long leave(String key) {
            leaves.incrementAndGet();
            return values.merge(key, -1L, Long::sum);
        }

        @Override
        public long inflight(String key) {
            return values.getOrDefault(key, 0L);
        }

        @Override
        public Map<String, Long> inflightBatch(List<String> keys) {
            return Map.of();
        }
    }

    private static TrafficPolicySource policySource() {
        return new TrafficPolicySource() {
            @Override
            public TrafficPolicies policiesOf(String resourceType, Long resourceId) {
                return new TrafficPolicies(new RateLimitPolicy(0, 1000), List.of());
            }

            @Override
            public Long providerAccountIdOf(String providerAccountCode) {
                return 99L;
            }
        };
    }

    private GatewayRequestHandler handler(ModelLoadStore loadStore, GatewayTrafficGuard guard, ProxyResult result) {
        UpstreamProxyClient proxy = new UpstreamProxyClient(null, null, null, null, null, null) {
            @Override
            public ProxyResult proxy(Map<String, Object> requestBody, MediaType contentType,
                                     RoutingRuleTargetDto target, ModelApiProtocolEnum protocol,
                                     String traceparent, String requestId) {
                return result;
            }
        };
        return new GatewayRequestHandler(proxy, null, guard, null, null, loadStore,
                loadKeys(), null,
                new OutageTracker(null, new OutageKeys("test")), new OutageKeys("test"),
                null, "traffic-topic", null, Schedulers.immediate(), null);
    }

    private static GatewayRequestContext context(List<RoutingRuleTargetDto> candidates) {
        GatewayRequestContext context = new GatewayRequestContext("Bearer sk", "rule-a",
                GatewayTraceContext.fromTraceparent(null), ModelApiProtocolEnum.CHAT_COMPLETIONS, null);
        context.routing(new ResolvedRouting(1L, "rule-a", 10L, "sk-routing-key", false,
                candidates, null, singleAttemptPolicy(), null));
        return context;
    }

    private static ModelLoadKeys loadKeys() {
        return new ModelLoadKeys("test");
    }

    private static RoutingRuleTargetDto target(String modelCode) {
        return new RoutingRuleTargetDto(modelCode, MODEL_ID, modelCode, "upstream-model",
                "模型", "CHAT", null, 1, true, null);
    }

    private static ProxyResult success() {
        return new ProxyResult("{\"id\":\"resp-1\"}", 1L, 2L, null);
    }

    /** 单次尝试：把「同目标重试」关掉，让「候选链是否只走一遍」成为唯一变量。 */
    private static RetryPolicy singleAttemptPolicy() {
        return new RetryPolicy(Boolean.TRUE, 1, 0, 0, 1.0, Boolean.FALSE, Boolean.FALSE,
                RetryPolicy.MODE_ALLOWLIST, List.of("5xx"), RetryPolicy.MODE_SIMPLE);
    }
}
