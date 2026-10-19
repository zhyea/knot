package org.chobit.knot.gateway.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.chobit.knot.gateway.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.gateway.model.GatewayErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 入站请求体大小上限过滤器：对非 multipart 请求按 {@code Content-Length} 拒绝超大请求体，
 * 防止上游 AI 调用的请求体被恶意放大打满网关内存。
 *
 * <p>豁免项：
 * <ul>
 *   <li>无 {@code Content-Length}（分块传输 / 流式）的请求——无法预先获知大小，交由下游处理；</li>
 *   <li>{@code multipart/form-data}——图片 / 音频 / 视频等多模态上传，体量本就大，
 *       由 Spring 的 {@code spring.servlet.multipart} 上限独立约束，避免误伤；</li>
 *   <li>{@code OPTIONS} 预检——无请求体。</li>
 * </ul>
 * </p>
 *
 * <p>阈值可配：{@code knot.gateway.max-request-body-size}（字节），默认 10MB。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MaxRequestBodySizeFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a new instance.
     */
    public MaxRequestBodySizeFilter(@Value("${knot.gateway.max-request-body-size:10485760}") long maxBytes,
                                   ObjectMapper objectMapper) {
        this.maxBytes = maxBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            filterChain.doFilter(request, response);
            return;
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBytes) {
            reject(response, contentLength);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, long contentLength) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Knot-Rejected-Reason", "payload_too_large");
        GatewayErrorResponse body = GatewayErrorResponse.of(
                "Request body too large: " + contentLength + " bytes exceeds limit of " + maxBytes + " bytes",
                GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code()
        );
        objectMapper.writeValue(response.getWriter(), body);
    }
}
