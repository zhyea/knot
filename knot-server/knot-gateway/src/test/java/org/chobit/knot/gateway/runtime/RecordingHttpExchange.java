package org.chobit.knot.gateway.runtime;

import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试替身：用内存 map 记录响应头与流式头，可预置请求头。
 *
 * <p>2026-10-20（WebFlux 批2）：原先测试直接用 {@code ServletHttpExchange} +
 * {@code MockHttpServletResponse}；容器切到 Netty 后 Servlet 类型整体消失，
 * 替身改为纯内存实现，也让「写头事实」变成可直接断言的 map，而不依赖 Servlet mock。</p>
 */
public final class RecordingHttpExchange implements GatewayHttpExchange {

    private final Map<String, String> requestHeaders = new LinkedHashMap<>();
    private final Map<String, String> responseHeaders = new LinkedHashMap<>();
    private MediaType streamingContentType;

    /**
     * 预置请求头。
     */
    public RecordingHttpExchange requestHeader(String name, String value) {
        requestHeaders.put(name, value);
        return this;
    }

    /**
     * 已写入的响应头；未写过返回 {@code null}。
     */
    public String responseHeader(String name) {
        return responseHeaders.get(name);
    }

    /**
     * {@link #prepareStreaming(MediaType)} 收到的内容类型。
     */
    public MediaType streamingContentType() {
        return streamingContentType;
    }

    @Override
    public String requestHeader(String name) {
        return requestHeaders.get(name);
    }

    @Override
    public void setResponseHeader(String name, String value) {
        responseHeaders.put(name, value);
    }

    @Override
    public void prepareStreaming(MediaType contentType) {
        this.streamingContentType = contentType;
    }

    @Override
    public DataBufferFactory bufferFactory() {
        return DefaultDataBufferFactory.sharedInstance;
    }
}
