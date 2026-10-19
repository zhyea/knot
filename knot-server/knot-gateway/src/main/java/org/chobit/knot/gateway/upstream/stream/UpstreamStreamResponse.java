package org.chobit.knot.gateway.upstream.stream;

import org.chobit.knot.gateway.adapter.request.UpstreamRequestAdapter;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

/**
 * 上游流式响应的句柄：持有尚未读取的响应体流，以及释放连接所需的原始响应对象。
 *
 * <p>必须在使用完毕后 {@link #close()}：Apache HttpClient 的响应关闭后连接才会归还连接池。</p>
 *
 * <p>{@code upstreamHeaders} 是上游响应头的<b>白名单快照</b>（见
 * {@code UpstreamRequestIdResolver}），在构造时一次性抓取，与底层连接的关闭解耦——
 * 关闭之后依然可读，供数仓消息与日志在流写完后取供应商侧请求标识（只取 request-id，
 * 不取 log-id，详见设计文档 §4.1）。</p>
 */
public final class UpstreamStreamResponse implements Closeable {

    private final InputStream body;
    private final MediaType contentType;
    private final UpstreamRequestContext context;
    private final UpstreamRequestAdapter adapter;
    private final ClientHttpResponse response;
    private final Map<String, String> upstreamHeaders;
    private final String upstreamRequestId;

    public UpstreamStreamResponse(InputStream body,
                                  MediaType contentType,
                                  UpstreamRequestContext context,
                                  UpstreamRequestAdapter adapter,
                                  ClientHttpResponse response) {
        this(body, contentType, context, adapter, response, Collections.emptyMap(), null);
    }

    public UpstreamStreamResponse(InputStream body,
                                  MediaType contentType,
                                  UpstreamRequestContext context,
                                  UpstreamRequestAdapter adapter,
                                  ClientHttpResponse response,
                                  Map<String, String> upstreamHeaders,
                                  String upstreamRequestId) {
        this.body = body;
        this.contentType = contentType;
        this.context = context;
        this.adapter = adapter;
        this.response = response;
        this.upstreamHeaders = upstreamHeaders == null ? Collections.emptyMap() : Collections.unmodifiableMap(upstreamHeaders);
        this.upstreamRequestId = upstreamRequestId;
    }

    public InputStream body() {
        return body;
    }

    public MediaType contentType() {
        return contentType;
    }

    public UpstreamRequestContext context() {
        return context;
    }

    public UpstreamRequestAdapter adapter() {
        return adapter;
    }

    /**
     * 上游响应头白名单快照（构造时抓取，close 后仍可读）；无命中为空 map，不为 null。
     */
    public Map<String, String> upstreamHeaders() {
        return upstreamHeaders;
    }

    /**
     * 上游供应商系统里这次请求的 id（白名单归一）；无命中为 null。
     */
    public String upstreamRequestId() {
        return upstreamRequestId;
    }

    /**
     * 上游是否按 SSE 返回：以此决定逐行转发（可注入用量事件）还是原样字节转发。
     */
    public boolean isEventStream() {
        return contentType != null && MediaType.TEXT_EVENT_STREAM.includes(contentType);
    }

    @Override
    public void close() throws IOException {
        try {
            body.close();
        } finally {
            response.close();
        }
    }
}
