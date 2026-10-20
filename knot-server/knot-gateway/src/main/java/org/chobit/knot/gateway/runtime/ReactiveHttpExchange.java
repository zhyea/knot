package org.chobit.knot.gateway.runtime;

import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;

import java.nio.charset.StandardCharsets;

/**
 * {@link GatewayHttpExchange} 的 WebFlux 实现（批2 起：容器是 Netty）。
 *
 * <p>与批1 的 Servlet 实现相比，这里<b>不需要任何 ThreadLocal</b>：
 * {@link ServerWebExchange} 是控制器方法入参，天然随链路传递，
 * 在 {@code boundedElastic} 线程上写响应头同样有效（响应对象本身是线程安全的）。</p>
 */
public final class ReactiveHttpExchange implements GatewayHttpExchange {

    private final ServerWebExchange exchange;

    public ReactiveHttpExchange(ServerWebExchange exchange) {
        this.exchange = exchange;
    }

    public ServerWebExchange exchange() {
        return exchange;
    }

    @Override
    public String requestHeader(String name) {
        return exchange.getRequest().getHeaders().getFirst(name);
    }

    @Override
    public void setResponseHeader(String name, String value) {
        exchange.getResponse().getHeaders().set(name, value);
    }

    @Override
    public void prepareStreaming(MediaType contentType) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.setContentType(withUtf8(contentType == null ? MediaType.TEXT_EVENT_STREAM : contentType));
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache");
        // 经过 Nginx 等反向代理时不要缓冲 SSE
        headers.set("X-Accel-Buffering", "no");
    }

    /**
     * 流式响应的字节就是字符串编码结果，文本类响应统一声明 UTF-8，
     * 与 Servlet 时代 {@code setCharacterEncoding("UTF-8")} 的效果保持一致。
     */
    private static MediaType withUtf8(MediaType contentType) {
        if (contentType.getCharset() != null) {
            return contentType;
        }
        String type = contentType.getType();
        if ("text".equalsIgnoreCase(type) || "json".equalsIgnoreCase(contentType.getSubtype())) {
            return new MediaType(contentType, StandardCharsets.UTF_8);
        }
        return contentType;
    }

    @Override
    public DataBufferFactory bufferFactory() {
        return exchange.getResponse().bufferFactory();
    }
}
