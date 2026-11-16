package org.chobit.knot.gateway.runtime;

import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.model.RetryPolicy;
import org.chobit.knot.gateway.load.ModelLoadKeys;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.outage.OutageKeys;
import org.chobit.knot.gateway.outage.OutageTracker;
import org.chobit.knot.gateway.redis.RedisUnavailableException;
import org.chobit.knot.gateway.upstream.stream.UpstreamStreamResponse;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式收尾链的隔离契约：释放在途 / 补记用量 / 发布事件三段，任一段失败都不得吃掉后面两段。
 *
 * <p>流式响应此时已提交，收尾异常既改不了状态码，也不能再上抛；但它造成的后果是真实的：
 * 释放失败 → 在途计数滞留到 TTL（LEAST_LOAD 持续偏斜）；记账失败 → 配额与成本漏记；
 * 事件失败 → 终态事件丢失。原实现是一条裸链，第一段抛异常后面两段直接不执行。</p>
 *
 * <p>两个层次各有一条证据：{@link #streamPreparationFailureReleasesDeferredLoad()} 验证
 * {@code streamResponse} 构造期的 try/catch 接线（延迟释放必须有 owner），其余用例验证
 * 收尾链本身的隔离与顺序。</p>
 */
class GatewayRequestHandlerStreamCompletionTest {

    private static final String MODEL = "MODEL";
    private static final long MODEL_ID = 7L;

    @Test
    @DisplayName("释放在途抛 Redis 故障：用量记账与流量事件仍执行")
    void loadReleaseFailureDoesNotBlockUsageAndTraffic() {
        GatewayRequestHandler handler = handler();
        List<String> ran = new ArrayList<>();

        handler.streamingCompletion(
                () -> {
                    throw new RedisUnavailableException("load", "leave", "load:model-a",
                            new IllegalStateException("redis down"));
                },
                () -> ran.add("usage"),
                () -> ran.add("traffic")).run();

        assertEquals(List.of("usage", "traffic"), ran,
                "第一段失败后，记账与事件必须照常执行——否则等于漏配额、漏终态事件");
        assertEquals(1L, handler.streamCompletionFailures(), "失败步必须计数（只靠 Reactor 日志等于没有告警）");
    }

    @Test
    @DisplayName("用量记账抛异常：流量事件仍执行")
    void usageFailureDoesNotBlockTraffic() {
        GatewayRequestHandler handler = handler();
        List<String> ran = new ArrayList<>();

        handler.streamingCompletion(
                () -> ran.add("release"),
                () -> {
                    throw new IllegalStateException("traffic guard down");
                },
                () -> ran.add("traffic")).run();

        assertEquals(List.of("release", "traffic"), ran);
        assertEquals(1L, handler.streamCompletionFailures());
    }

    @Test
    @DisplayName("正常路径：顺序恒为 释放 → 记账 → 事件")
    void completionRunsInFixedOrder() {
        GatewayRequestHandler handler = handler();
        List<String> ran = new ArrayList<>();

        handler.streamingCompletion(() -> ran.add("release"),
                () -> ran.add("usage"),
                () -> ran.add("traffic")).run();

        assertEquals(List.of("release", "usage", "traffic"), ran,
                "释放必须最先（后续步骤可能要用到已归零的在途读数）");
        assertEquals(0L, handler.streamCompletionFailures());
    }

    @Test
    @DisplayName("请求没有在途计数时容忍 null，不影响后两段")
    void nullLoadReleaseIsTolerated() {
        GatewayRequestHandler handler = handler();
        List<String> ran = new ArrayList<>();

        handler.streamingCompletion(null, () -> ran.add("usage"), () -> ran.add("traffic")).run();

        assertEquals(List.of("usage", "traffic"), ran);
        assertEquals(0L, handler.streamCompletionFailures(), "null 是合法输入，不记失败");
    }

    @Test
    @DisplayName("流式构造期抛异常：延迟释放必须归还（否则在途只能等 TTL）")
    void streamPreparationFailureReleasesDeferredLoad() {
        GatewayRequestHandler handler = handler();
        AtomicInteger released = new AtomicInteger();
        GatewayRequestContext context = context();
        // 属性名与 GatewayRequestHandler.ATTR_LOAD_RELEASE 同源（私有常量，只能按字符串写）
        context.putAttribute("loadRelease", (Runnable) released::incrementAndGet);

        GatewayHttpExchange throwing = new GatewayHttpExchange() {
            @Override
            public String requestHeader(String name) {
                return null;
            }

            @Override
            public void setResponseHeader(String name, String value) {
            }

            @Override
            public void prepareStreaming(MediaType contentType) {
                throw new IllegalStateException("illegal content type");
            }

            @Override
            public DataBufferFactory bufferFactory() {
                return DefaultDataBufferFactory.sharedInstance;
            }
        };
        GatewayExchange failing = new GatewayExchange(Map.of("model", MODEL),
                MediaType.APPLICATION_JSON, throwing);

        ProxyResult result = new ProxyResult(null, 1L, MODEL_ID, null, streamResponse(),
                null, null, null);

        assertThrows(IllegalStateException.class,
                () -> handler.streamResponse(result, context.routing(), context, failing));

        assertEquals(1, released.get(),
                "构造失败时 releaseDeferred 已置位、候选 Mono 的 doFinally 不会兜底，必须在这里归还");
    }

    // ---------- 夹具 ----------

    /**
     * 只用于驱动收尾链：其余协作者全 null，{@code streamingCompletion} 只用 metrics 与计数器。
     */
    private static GatewayRequestHandler handler() {
        return new GatewayRequestHandler(null, null, null, null, null, null, null, null,
                new OutageTracker(null, new OutageKeys("test")), new OutageKeys("test"),
                null, "traffic-topic", null, Schedulers.immediate(), null);
    }

    private static GatewayRequestContext context() {
        GatewayRequestContext context = new GatewayRequestContext("Bearer sk", "rule-a",
                GatewayTraceContext.fromTraceparent(null), ModelApiProtocolEnum.CHAT_COMPLETIONS, null);
        context.routing(new ResolvedRouting(1L, "rule-a", 10L, "sk-routing-key", false,
                List.of(target()), null, singleAttemptPolicy(), null));
        return context;
    }

    private static UpstreamStreamResponse streamResponse() {
        return new UpstreamStreamResponse(Flux.empty(),
                MediaType.TEXT_EVENT_STREAM, null, null, null, null);
    }

    private static RoutingRuleTargetDto target() {
        return new RoutingRuleTargetDto(MODEL, MODEL_ID, MODEL, "upstream-model",
                "模型", "CHAT", null, 1, true, null);
    }

    private static RetryPolicy singleAttemptPolicy() {
        return new RetryPolicy(Boolean.TRUE, 1, 0, 0, 1.0, Boolean.FALSE, Boolean.FALSE,
                RetryPolicy.MODE_ALLOWLIST, List.of("5xx"), RetryPolicy.MODE_SIMPLE);
    }
}
