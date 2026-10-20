package org.chobit.knot.gateway.runtime;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

/**
 * 流式响应载体：控制器拿到它之后直接把 {@link #body()} 交给容器写出。
 *
 * <p>2026-10-20（WebFlux 批2）：取代 {@code StreamingResponseBody}。链路里不再有
 * 「一个会在后台线程里阻塞读写的回调」，而是一个待订阅的流——订阅即转发，取消即停止。</p>
 *
 * <p>放在 {@code runtime} 包而不是 {@code upstream} 包：模板（{@code AbstractGatewayRequestTemplate}）
 * 需要识别它来决定「要不要在这里写首 token 头」，而模板不认识 upstream 内部类型。</p>
 */
public record GatewayStreamingResponse(MediaType contentType, Flux<DataBuffer> body) {
}
