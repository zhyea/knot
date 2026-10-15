package org.chobit.knot.gateway.runtime;

import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 过滤器级行为：{@link GatewayTraceFilter} 必须在请求处理期间把 traceId / spanId 放进 MDC
 * （日志 pattern 消费 %X{traceId} / %X{spanId}），并在响应头回写 traceparent，
 * 请求结束后清理线程绑定，避免线程池复用导致串号。
 */
class GatewayTraceFilterTest {

    private static final Pattern W3C = Pattern.compile(
            "^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");

    private final GatewayTraceFilter filter = new GatewayTraceFilter();

    @BeforeEach
    @AfterEach
    void clearBinding() {
        GatewayTraceContext.clearCurrent();
        MDC.clear();
    }

    @Test
    void populatesMdcAndResponseHeaderWhenClientOmitsTraceparent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/openai/v1/chat/completions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] mdcTraceId = new String[1];
        String[] mdcSpanId = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                // 模拟请求处理期间打的日志：此刻 MDC 必须已有值
                mdcTraceId[0] = MDC.get(GatewayTraceFilter.MDC_TRACE_ID);
                mdcSpanId[0] = MDC.get(GatewayTraceFilter.MDC_SPAN_ID);
                GatewayTraceContext bound = GatewayTraceContext.currentOrCreate(null);
                assertNotNull(bound, "处理期间 currentOrCreate 必须拿到 filter 绑定的上下文");
                assertEquals(mdcTraceId[0], bound.traceId());
                assertEquals(mdcSpanId[0], bound.spanId());
            }
        };

        filter.doFilter(request, response, chain);

        String traceparent = response.getHeader(GatewayHeaders.TRACEPARENT);
        assertNotNull(traceparent, "响应必须回写 traceparent");
        var m = W3C.matcher(traceparent);
        assertTrue(m.matches(), "响应 traceparent 必须合规: " + traceparent);
        assertEquals(m.group(2), mdcTraceId[0], "日志 traceId 必须与响应头一致");
        assertEquals(m.group(3), mdcSpanId[0], "日志 spanId 必须与响应头一致");
        // 请求结束必须清理，否则线程池复用会串号
        assertNull(MDC.get(GatewayTraceFilter.MDC_TRACE_ID));
        assertNull(MDC.get(GatewayTraceFilter.MDC_SPAN_ID));
        assertNull(MDC.get(GatewayTraceFilter.MDC_TRACEPARENT));
    }

    @Test
    void preservesClientTraceIdButReplacesSpanIdForValidHeader() throws Exception {
        String incoming = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/openai/v1/chat/completions");
        request.addHeader(GatewayHeaders.TRACEPARENT, incoming);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String traceparent = response.getHeader(GatewayHeaders.TRACEPARENT);
        var m = W3C.matcher(traceparent);
        assertTrue(m.matches(), "响应 traceparent 必须合规: " + traceparent);
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", m.group(2), "合法入站必须保留客户端 traceId");
        assertNotEquals("00f067aa0ba902b7", m.group(3), "span 段必须是网关自己的新 spanId");
        assertEquals("01", m.group(4), "sampled 标志必须透传");
    }

    @Test
    void generatesDefaultForMalformedHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/openai/v1/chat/completions");
        request.addHeader(GatewayHeaders.TRACEPARENT, "not-a-traceparent");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String traceparent = response.getHeader(GatewayHeaders.TRACEPARENT);
        assertTrue(W3C.matcher(traceparent).matches(), "非法头必须回退为合规默认值: " + traceparent);
    }

    @Test
    void cleansMdcEvenWhenDownstreamThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/openai/v1/chat/completions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                throw new IllegalStateException("boom");
            }
        };

        try {
            filter.doFilter(request, response, chain);
        } catch (IllegalStateException expected) {
            // 下游抛错也要走 finally 清理
        }
        assertNull(MDC.get(GatewayTraceFilter.MDC_TRACE_ID), "异常路径也必须清理 MDC");
        assertNull(MDC.get(GatewayTraceFilter.MDC_SPAN_ID));
    }
}
