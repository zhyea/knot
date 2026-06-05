package org.chobit.knot.gateway.runtime;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayTraceFilter extends OncePerRequestFilter {

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";
    public static final String MDC_TRACEPARENT = "traceparent";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        GatewayTraceContext trace = GatewayTraceContext.fromTraceparent(request.getHeader(GatewayHeaders.TRACEPARENT));
        GatewayTraceContext.setCurrent(trace);
        MDC.put(MDC_TRACE_ID, trace.traceId());
        MDC.put(MDC_SPAN_ID, trace.spanId());
        MDC.put(MDC_TRACEPARENT, trace.traceparent());
        response.setHeader(GatewayHeaders.TRACEPARENT, trace.traceparent());
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACEPARENT);
            MDC.remove(MDC_SPAN_ID);
            MDC.remove(MDC_TRACE_ID);
            GatewayTraceContext.clearCurrent();
        }
    }
}
