package org.chobit.knot.gateway.runtime;

import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 请求级追踪上下文入口：解析 / 生成 W3C traceparent，回写响应头，并写入 Reactor Context。
 *
 * <p>2026-10-20（WebFlux 批2）：从 {@code OncePerRequestFilter} 改为 {@link WebFilter}，
 * 追踪信息<b>不再放 ThreadLocal</b>（批1 时代的 {@code GatewayTraceContext.setCurrent} 已删）。
 * 换 Netty 后一次请求会跨多个线程，线程本地属性无处安放；Reactor Context 随订阅链传递，
 * 模板用 {@code Mono.deferContextual} 取用。</p>
 *
 * <p>⚠ 这里<b>刻意不碰 MDC</b>：event loop 线程被大量请求复用，写进去就是串号。
 * 阻塞段任务线程上的 MDC 由 {@link MdcContextSupport#onBlockingScheduler} 绑定。</p>
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
