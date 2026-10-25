package org.chobit.knot.gateway.runtime;

import org.chobit.knot.common.constants.GatewayHeaders;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求模板的「零 ThreadLocal / 零 RequestContext」契约。
 *
 * <p>原先模板靠 {@code RequestContextHolder} 的线程本地属性到处隐式取 response，并在每个异步回调里
 * 手工重绑。换成 Reactor 线程模型后这套隐式传递必然失效——而且<b>不报错</b>：写头被静默跳过，
 * 调用方只是「少了几个响应头」，没有任何异常提示。本类用「在<b>没有请求上下文</b>的线程上跑完整模板」
 * 的方式把这条契约钉死。</p>
 *
 * <p>2026-10-20（WebFlux 批2）：容器是 Netty，Servlet 版的载体实现已删除，
 * 测试替身改为纯内存的 {@link RecordingHttpExchange}。</p>
 */
class AbstractGatewayRequestTemplateCarrierTest {

    private static final String RESPONSE_BODY = "{\"id\":\"chat-1\"}";

    @Test
    @DisplayName("无请求上下文线程上，首 token 头仍写到显式载体")
    void writesFirstTokenHeaderWithoutRequestContext() throws Exception {
        RecordingHttpExchange carrier = new RecordingHttpExchange();
        StubTemplate template = new StubTemplate(routing(false));

        AtomicReference<Object> body = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> template
                .handleMultipart("Bearer key", "rule-a", null, null, Map.of("model", "x"),
                        ModelApiProtocolEnum.CHAT_COMPLETIONS, carrier)
                .subscribe(body::set, failure::set));
        worker.start();
        worker.join();

        assertNull(failure.get(), "异步线程上不得因缺少请求上下文而失败");
        assertEquals(RESPONSE_BODY, body.get(), "非流式响应体原样返回");
        String ttft = carrier.responseHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME);
        assertNotNull(ttft, "显式载体必须收到首 token 时间头");
        assertTrue(Long.parseLong(ttft) >= 0);
    }

    @Test
    @DisplayName("无载体（noop）时不写头也不炸链路")
    void degradesSilentlyWithoutCarrier() {
        StubTemplate template = new StubTemplate(routing(false));

        Object body = template.handleMultipart("Bearer key", "rule-a", null, null,
                Map.of("model", "x"), ModelApiProtocolEnum.CHAT_COMPLETIONS, GatewayHttpExchange.noop()).block();

        assertEquals(RESPONSE_BODY, body);
    }

    @Test
    @DisplayName("入口即拒（缺 Bearer）以错误信号返回，而非同步抛出漏出订阅")
    void headerValidationFailureSurfacesAsErrorSignal() {
        StubTemplate template = new StubTemplate(routing(false));

        Mono<Object> mono = template.handleMultipart("Basic abc", "rule-a", null, null,
                Map.of("model", "x"), ModelApiProtocolEnum.CHAT_COMPLETIONS, GatewayHttpExchange.noop());

        GatewayAuthException failure = assertThrows(GatewayAuthException.class, mono::block);
        assertTrue(failure.getMessage().contains("Invalid API key"));
    }

    @Test
    @DisplayName("traceparent 只在无关上下文时自建：Reactor Context 里的那一份优先")
    void prefersTraceContextFromReactorContext() {
        StubTemplate template = new StubTemplate(routing(false));
        GatewayTraceContext injected = GatewayTraceContext.fromTraceparent(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        GatewayRequestContext[] captured = new GatewayRequestContext[1];
        template.contextSink = context -> captured[0] = context;

        template.handleMultipart("Bearer key", "rule-a", null, null, Map.of("model", "x"),
                        ModelApiProtocolEnum.CHAT_COMPLETIONS, GatewayHttpExchange.noop())
                .contextWrite(context -> context.put(GatewayTraceContext.CONTEXT_KEY, injected))
                .block();

        assertNotNull(captured[0]);
        assertEquals(injected.traceId(), captured[0].traceId(),
                "必须用 filter 写进 Reactor Context 的那一份，否则异步线程上 traceId 会对不上");
    }

    private static ResolvedRouting routing(boolean returnUsageDetail) {
        return new ResolvedRouting(1L, "rule-a", 10L, "sk-routing-key", returnUsageDetail,
                List.of(), null, null, null);
    }

    /**
     * 最小模板：不接插件 / Kafka / 词库，只验证「上下文与载体传递」这一条链路。
     */
    private static final class StubTemplate extends AbstractGatewayRequestTemplate {

        private final ResolvedRouting routing;
        private java.util.function.Consumer<GatewayRequestContext> contextSink;

        private StubTemplate(ResolvedRouting routing) {
            super(null, null, null, null);
            this.routing = routing;
        }

        @Override
        protected ResolvedRouting resolveRouting(GatewayRequestContext context) {
            if (contextSink != null) {
                contextSink.accept(context);
            }
            return routing;
        }

        @Override
        protected Mono<ProxyResult> proxy(GatewayRequestContext context, GatewayExchange exchange) {
            return Mono.just(new ProxyResult(RESPONSE_BODY, 1L, 2L, null));
        }

        @Override
        protected Object applyUsageAccounting(GatewayRequestContext context, GatewayExchange exchange) {
            return exchange.proxyResult().responseBody();
        }
    }
}
