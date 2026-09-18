package org.chobit.knot.gateway.upstream.stream;

import org.chobit.knot.gateway.adapter.request.UpstreamRequestAdapter;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * 上游流式响应的句柄：持有尚未读取的响应体流，以及释放连接所需的原始响应对象。
 *
 * <p>必须在使用完毕后 {@link #close()}：Apache HttpClient 的响应关闭后连接才会归还连接池。</p>
 */
public final class UpstreamStreamResponse implements Closeable {

    private final InputStream body;
    private final MediaType contentType;
    private final UpstreamRequestContext context;
    private final UpstreamRequestAdapter adapter;
    private final ClientHttpResponse response;

    public UpstreamStreamResponse(InputStream body,
                                  MediaType contentType,
                                  UpstreamRequestContext context,
                                  UpstreamRequestAdapter adapter,
                                  ClientHttpResponse response) {
        this.body = body;
        this.contentType = contentType;
        this.context = context;
        this.adapter = adapter;
        this.response = response;
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
