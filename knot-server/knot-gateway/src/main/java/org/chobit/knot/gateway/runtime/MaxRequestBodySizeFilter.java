package org.chobit.knot.gateway.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chobit.knot.common.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.gateway.model.GatewayErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 入站请求体大小上限过滤器：对非 multipart 请求按 {@code Content-Length} 拒绝超大请求体。
 * 豁免：无 {@code Content-Length}（分块传输 / 流式）的请求、{@code multipart/form-data}（由 {@code spring.webflux.multipart} 的上限独立约束）、{@code OPTIONS} 预检。
 * 阈值可配：{@code knot.gateway.max-request-body-size}（字节），默认 10MB。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MaxRequestBodySizeFilter implements WebFilter {

    private final long maxBytes;
    private final ObjectMapper objectMapper;

    public MaxRequestBodySizeFilter(@Value("${knot.gateway.max-request-body-size:10485760}") long maxBytes,
                                   ObjectMapper objectMapper) {
        this.maxBytes = maxBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }
        MediaType contentType = request.getHeaders().getContentType();
        if (contentType != null && MediaType.MULTIPART_FORM_DATA.includes(contentType)) {
            return chain.filter(exchange);
        }
        long contentLength = request.getHeaders().getContentLength();
        if (contentLength > maxBytes) {
            return reject(exchange.getResponse(), contentLength);
        }
        return chain.filter(exchange);
    }

    private Mono<Void> reject(ServerHttpResponse response, long contentLength) {
        response.setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("X-Knot-Rejected-Reason", "payload_too_large");
        GatewayErrorResponse body = GatewayErrorResponse.of(
                "Request body too large: " + contentLength + " bytes exceeds limit of " + maxBytes + " bytes",
                GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code()
        );
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // 序列化失败只能放弃本次响应体：状态码 413 已经写下，别再往上抛把状态覆盖成 500
            return response.setComplete();
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
