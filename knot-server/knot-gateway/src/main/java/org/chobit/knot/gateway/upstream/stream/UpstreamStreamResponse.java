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
 *
 * <p>2026-10-20（WebFlux 批2）：<b>不再需要 InputStream 桥接与 close()</b>。
 * 批1 时代容器还是 Servlet，必须用 {@code PipedInputStream} 把 Flux 泵成阻塞流；
 * 切到 Netty 后响应体直接以 {@code Flux<DataBuffer>} 交给容器写出，
 * 取消 / 结束由 Reactor 自动完成，连接由 reactor-netty 在流终止时归还。</p>
 *
 * <p>附带收益：管道桥接会丢错误（写端出错只能关管道，读端只见 {@code -1}，看起来像正常结束），
 * 批1 需要用 {@code pumpError()} 手工补抛；现在错误沿 {@code onError} 原样传播，
 * 上游中途失败一定能被调用方看到。</p>
 *
 * <p>{@code upstreamHeaders} 是上游响应头的<b>白名单快照</b>（见
 * {@code UpstreamRequestIdResolver}），构造时一次性抓取，与连接生命周期解耦——
 * 流写完后取供应商侧请求标识（只取 request-id）依然可读。</p>
 *
 * <p>2026-10-25：重新持有 {@link UpstreamAdapter}（2026-10-23 计费去兜底时曾移除）——
 * 理由与计费无关：流式路径的响应适配（{@link UpstreamAdapter#streamAdapter}）
 * 需要适配器在场，与整包路径的 {@code handleResponse} 对称。为 {@code null} 时
 * 流式响应按透传处理。</p>
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
