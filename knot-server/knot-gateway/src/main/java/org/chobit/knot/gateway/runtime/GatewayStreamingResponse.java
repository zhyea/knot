package org.chobit.knot.gateway.runtime;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

/**
 * 流式响应载体：控制器拿到后把 {@link #body()} 交给容器写出；订阅即转发，取消即停止。
 */
public record GatewayStreamingResponse(MediaType contentType, Flux<DataBuffer> body) {
}
