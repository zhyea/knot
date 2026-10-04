package org.chobit.knot.gateway.service;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * 流式路由测试持有的网关响应句柄。
 *
 * <p>由 {@code RestClient.exchange(ExchangeFunction, false)} 构造：不自动关闭响应，
 * 因此响应体保持可增量读取。必须实现 {@link Closeable}，由
 * {@code StreamingResponseBody.writeTo()} 以 try-with-resources 关闭，
 * 确保浏览器取消、上游异常等路径都能把连接归还连接池。</p>
 */
public class RoutingTestStreamHandle implements Closeable {

    private final ClientHttpResponse response;
    private final RoutingTestPreparation preparation;
    private boolean closed;

    /**
     * Constructs a new instance.
     */
    public RoutingTestStreamHandle(ClientHttpResponse response, RoutingTestPreparation preparation) {
        this.response = response;
        this.preparation = preparation;
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public RoutingTestPreparation preparation() {
        return preparation;
    }

    /**
     * 网关响应的输入流（增量读取）。
     */
    public InputStream bodyStream() throws IOException {
        return response.getBody();
    }

    /**
     * 网关真实 HTTP 状态（SSE 外层已是 200，真实状态由该值承载）。
     */
    public HttpStatusCode statusCode() throws IOException {
        return response.getStatusCode();
    }

    /**
     * 网关响应 Content-Type，用于区分文本 / SSE 与二进制。
     */
    public MediaType contentType() {
        return response.getHeaders().getContentType();
    }

    /**
     * Releases the resource. Executes the public operation.
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        response.close();
    }
}
