package org.chobit.knot.gateway.runtime;

import org.chobit.knot.common.constants.GatewayHeaders;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 请求级追踪上下文入口：解析 / 生成 W3C traceparent，回写响应头，并写入 Reactor Context。
 * 本过滤器不写 MDC，阻塞段任务线程上的 MDC 由 {@link MdcContextSupport#onBlockingScheduler} 绑定。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayTraceFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        GatewayTraceContext trace = GatewayTraceContext.fromTraceparent(
                exchange.getRequest().getHeaders().getFirst(GatewayHeaders.TRACEPARENT));
        // 响应头在任何写出之前设置：响应一旦 commit 就改不动了
        exchange.getResponse().getHeaders().set(GatewayHeaders.TRACEPARENT, trace.traceparent());
        return chain.filter(exchange)
                .contextWrite(context -> context.put(GatewayTraceContext.CONTEXT_KEY, trace));
    }
}
