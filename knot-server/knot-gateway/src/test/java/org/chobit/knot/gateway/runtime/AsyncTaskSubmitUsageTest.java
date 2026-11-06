package org.chobit.knot.gateway.runtime;

import org.chobit.knot.adapter.model.usage.NormalizedUsage;
import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.async.AsyncTaskSupport;
import org.chobit.knot.gateway.load.CaffeineModelLoadStore;
import org.chobit.knot.gateway.load.ModelLoadKeys;
import org.chobit.knot.gateway.model.GatewayRoutingContext;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskAcceptedResponse;
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

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 异步提交不得把创建回执当消费完成。
 *
 * <p>两条断言缺一不可：事件侧必须先摘掉代理链路推出来的占位 {@code usage/billing}；
 * 限额侧必须在异步识别为真时跳过记账，否则后续查询即使失败，额度也已经被扣掉。</p>
 */
class AsyncTaskSubmitUsageTest {

    private static final String CREATE_BODY = "{\"id\":\"up-1\"}";

    @Test
    @DisplayName("异步提交：限额不记账，事件 usage/billing 被摘掉")
    void asyncSubmitDoesNotConsumeQuotaOrPublishUsage() {
        RecordingGuard guard = new RecordingGuard();
        GatewayRequestHandler handler = handler(guard, true);
        GatewayRequestContext context = context();
        GatewayExchange exchange = new GatewayExchange(Map.of("model", "model-a", "size", "1280x720"));

        ProxyResult result = handler.proxy(context, exchange).block();
        exchange.proxyResult(result);

        assertEquals(0, guard.records, "提交只完成 create，真实用量要到终态查询之后");
        Object response = handler.applyUsageAccounting(context, exchange);

        assertInstanceOf(AsyncTaskAcceptedResponse.class, response);
        assertNull(exchange.proxyResult().usage(),
                "提交事件必须没有 usage/billing，否则会生成 task_terminal=false 的假账");
    }

    @Test
    @DisplayName("对照：同步请求仍按上游结果记账")
    void syncRequestStillConsumesQuota() {
        RecordingGuard guard = new RecordingGuard();
        GatewayRequestHandler handler = handler(guard, false);

        handler.proxy(context(), new GatewayExchange(Map.of("model", "model-a"))).block();

        assertEquals(1, guard.records, "同步请求没有第二次结算机会，必须在请求成功时记账");
    }

    private static GatewayRequestHandler handler(RecordingGuard guard, boolean async) {
        CaffeineModelLoadStore loadStore = new CaffeineModelLoadStore(100L, 60L);
        OutageKeys outageKeys = new OutageKeys("test");
        UpstreamProxyClient proxy = new UpstreamProxyClient(null, null, null, null, null, null) {
            @Override
            public ProxyResult proxy(Map<String, Object> requestBody, MediaType contentType,
                                     RoutingRuleTargetDto target, ModelApiProtocolEnum protocol,
                                     String traceparent, String requestId) {
                return new ProxyResult(CREATE_BODY, 7L, 7L, usage());
            }
        };
        AsyncTaskSupport asyncSupport = new AsyncTaskSupport(null, null, null, null) {
            @Override
            public boolean isAsync(RoutingRuleTargetDto target, ModelApiProtocolEnum protocol) {
                return async;
            }

            @Override
            public AsyncTaskAcceptedResponse submitIfAsync(GatewayRequestContext context,
                                                           GatewayExchange exchange,
                                                           ProxyResult result) {
                return async ? AsyncTaskAcceptedResponse.accepted("tk-1") : null;
            }
        };
        return new GatewayRequestHandler(proxy, null, guard, null, null, loadStore,
                new ModelLoadKeys("test"), null, new OutageTracker(null, outageKeys), outageKeys,
                null, "traffic-topic", null, null, asyncSupport);
    }

    private static GatewayRequestContext context() {
        RoutingRuleTargetDto target = new RoutingRuleTargetDto("model-a", 7L, "model-a", "upstream-a",
                "模型", "VIDEO", "pa-1", 1, true, "pool-1");
        GatewayRoutingContext routingContext = new GatewayRoutingContext(
                null,
                new GatewayRoutingContext.ConsumerSnapshot(2L, "consumer", "消费者", null, false),
                new GatewayRoutingContext.AppSnapshot(3L, "app", "应用", null),
                null, null, null);
        GatewayRequestContext context = new GatewayRequestContext("Bearer sk", "rule-a",
                GatewayTraceContext.fromTraceparent(null),
                ModelApiProtocolEnum.VIDEO_GENERATIONS, null);
        context.routing(new ResolvedRouting(1L, "rule-a", 2L, "sk", false,
                List.of(target), routingContext, null, null));
        return context;
    }

    private static UsageAccounting usage() {
        return UsageAccounting.of(null, null, null, null,
                new NormalizedUsage(0L, "0.8", "USD", "v1", List.of()));
    }

    private static final class RecordingGuard extends GatewayTrafficGuard {

        private int records;

        private RecordingGuard() {
            super(new TrafficPolicySource() {
                @Override
                public TrafficPolicies policiesOf(String resourceType, Long resourceId) {
                    return null;
                }

                @Override
                public Long providerAccountIdOf(String providerAccountCode) {
                    return 9L;
                }
            }, new CaffeineTrafficCounterStore(100L),
                    new TrafficCounterKeys("test", ZoneId.of("Asia/Shanghai")));
        }

        @Override
        public void record(ResolvedRouting routing, RoutingRuleTargetDto target,
                           org.chobit.knot.gateway.traffic.TrafficUsage usage,
                           TrafficCheckContext context) {
            records++;
        }
    }
}
