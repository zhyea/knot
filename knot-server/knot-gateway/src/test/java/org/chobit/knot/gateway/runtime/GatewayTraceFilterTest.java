package org.chobit.knot.gateway.runtime;

import org.chobit.knot.common.constants.GatewayHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 追踪入口过滤器（{@link GatewayTraceFilter}）的契约。
 *
 * <p>2026-10-20（WebFlux 批2）：从 {@code OncePerRequestFilter} 改为 {@link WebFilter}，
 * 断言口径同步变化——跑在 Netty 后追踪信息<b>不再放 MDC / ThreadLocal</b>，
 * 而是写进 Reactor Context（同一请求会跨多个线程，线程本地属性在那里无处安放）。</p>
 *
 * <p>MDC 的绑定时机改为「阻塞段任务线程」（见 {@link MdcContextSupportTest}）：
 * event loop 线程被大量请求复用，在那里写 MDC 只会串号。</p>
 */
class GatewayTraceFilterTest {

    private static final Pattern W3C = Pattern.compile(
            "^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");

    private final GatewayTraceFilter filter = new GatewayTraceFilter();

    @Test
    @DisplayName("客户端未带 traceparent：生成合规 trace，写响应头并放进 Reactor Context")
    void generatesTraceAndPublishesIntoContext() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/openai/v1/chat/completions").build());
        AtomicReference<GatewayTraceContext> seenInChain = new AtomicReference<>();

        filter.filter(exchange, contextCapturingChain(seenInChain)).block();

        String traceparent = exchange.getResponse().getHeaders().getFirst(GatewayHeaders.TRACEPARENT);
        assertNotNull(traceparent, "响应必须回写 traceparent");
        Matcher matcher = W3C.matcher(traceparent);
        assertTrue(matcher.matches(), "响应 traceparent 必须合规: " + traceparent);

        GatewayTraceContext fromContext = seenInChain.get();
        assertNotNull(fromContext, "链路内必须能从 Reactor Context 取到追踪上下文");
        assertEquals(matcher.group(2), fromContext.traceId(), "响应头 traceId 必须与链路内一致");
        assertEquals(matcher.group(3), fromContext.spanId(), "响应头 spanId 必须与链路内一致");
    }

    @Test
    @DisplayName("合法入站头：保留客户端 traceId，换成网关自己的 spanId")
    void preservesClientTraceIdButReplacesSpanId() {
        String incoming = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/openai/v1/chat/completions")
                        .header(GatewayHeaders.TRACEPARENT, incoming)
                        .build());

        filter.filter(exchange, chain -> Mono.empty()).block();

        String traceparent = exchange.getResponse().getHeaders().getFirst(GatewayHeaders.TRACEPARENT);
        Matcher matcher = W3C.matcher(traceparent);
        assertTrue(matcher.matches(), "响应 traceparent 必须合规: " + traceparent);
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", matcher.group(2), "合法入站必须保留客户端 traceId");
        assertNotEquals("00f067aa0ba902b7", matcher.group(3), "span 段必须是网关自己的新 spanId");
        assertEquals("01", matcher.group(4), "sampled 标志必须透传");
    }

    @Test
    @DisplayName("非法入站头：回退为合规默认值")
    void generatesDefaultForMalformedHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/openai/v1/chat/completions")
                        .header(GatewayHeaders.TRACEPARENT, "not-a-traceparent")
                        .build());

        filter.filter(exchange, chain -> Mono.empty()).block();

        String traceparent = exchange.getResponse().getHeaders().getFirst(GatewayHeaders.TRACEPARENT);
        assertTrue(W3C.matcher(traceparent).matches(), "非法头必须回退为合规默认值: " + traceparent);
    }

    @Test
    @DisplayName("过滤器本身不碰 MDC：event loop 线程上写 MDC 会串号")
    void doesNotTouchMdcOnEventLoopThread() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/openai/v1/chat/completions").build());
        AtomicReference<String> mdcInsideChain = new AtomicReference<>();

        // filter 返回的 Mono 在被订阅时才执行，此刻 MDC 必须是干净的
        filter.filter(exchange, ex -> Mono.defer(() -> {
            mdcInsideChain.set(MDC.get(GatewayTraceContext.MDC_TRACE_ID));
            return Mono.empty();
        })).block();

        assertNull(mdcInsideChain.get(), "filter 不得在链路线程上写 MDC，实际 " + mdcInsideChain.get());
        assertNull(MDC.get(GatewayTraceContext.MDC_TRACE_ID), "调用线程 MDC 必须保持干净");
    }

    /**
     * 捕获 Reactor Context 里的追踪上下文后正常结束链路。
     */
    private static WebFilterChain contextCapturingChain(AtomicReference<GatewayTraceContext> sink) {
        return exchange -> Mono.deferContextual(view -> {
            sink.set(view.getOrDefault(GatewayTraceContext.CONTEXT_KEY, null));
            return Mono.empty();
        });
    }
}
