package org.chobit.knot.gateway.upstream.stream;

import org.chobit.knot.adapter.protocol.UpstreamAdapter;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.Map;

/**
 * 上游流式响应的句柄：持有尚未消费的响应体 {@link Flux}，以及响应元数据快照。
 * 响应体以 {@code Flux<DataBuffer>} 交给容器写出，取消 / 结束由 Reactor 完成，连接由 reactor-netty 在流终止时归还；错误沿 {@code onError} 原样传播。
 * {@code upstreamHeaders} 是上游响应头的白名单快照（见 {@code UpstreamRequestIdResolver}），构造时一次性抓取，与连接生命周期解耦，流写完后仍可读供应商侧请求标识。
 * {@link UpstreamAdapter} 供流式响应适配（{@link UpstreamAdapter#streamAdapter}）；为 {@code null} 时按透传处理。
 */
public final class UpstreamStreamResponse {

    private final Flux<DataBuffer> body;
    private final MediaType contentType;
    private final UpstreamRequestContext context;
    private final UpstreamAdapter adapter;
    private final Map<String, String> upstreamHeaders;
    private final String upstreamRequestId;

    public UpstreamStreamResponse(Flux<DataBuffer> body,
                                  MediaType contentType,
                                  UpstreamRequestContext context,
                                  UpstreamAdapter adapter,
                                  Map<String, String> upstreamHeaders,
                                  String upstreamRequestId) {
        this.body = body == null ? Flux.empty() : body;
        this.contentType = contentType;
        this.context = context;
        this.adapter = adapter;
        this.upstreamHeaders = upstreamHeaders == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(upstreamHeaders);
        this.upstreamRequestId = upstreamRequestId;
    }

    /**
     * 上游响应体（懒消费）：订阅即开始从上游收数据，取消即停止并归还连接。
     */
    public Flux<DataBuffer> body() {
        return body;
    }

    public MediaType contentType() {
        return contentType;
    }

    public UpstreamRequestContext context() {
        return context;
    }

    /**
     * 本次请求选定的上游适配器；为 {@code null} 时流式响应适配按透传处理。
     */
    public UpstreamAdapter adapter() {
        return adapter;
    }

    /**
     * 上游响应头白名单快照（构造时抓取）；无命中为空 map，不为 null。
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
}
