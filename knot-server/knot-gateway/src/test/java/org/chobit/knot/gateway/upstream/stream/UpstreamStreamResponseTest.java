package org.chobit.knot.gateway.upstream.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式响应句柄的两条契约：<b>上游头快照</b>与<b>响应体生命周期</b>。
 *
 * <p>2026-10-20（WebFlux 批2）：句柄不再持有 {@code InputStream}、不再 {@code close()}。
 * 批1 用管道把 {@code Flux} 泵成阻塞流，必须靠 {@code close()} 收尾、靠 {@code pumpError()}
 * 补抛上游中断；现在响应体就是 {@code Flux<DataBuffer>}，取消由订阅方完成，
 * 错误沿 {@code onError} 原样传播（对应行为断言在 {@link ProxyStreamingFluxTest}）。</p>
 *
 * <p>响应头快照仍然必须与连接生命周期解耦——数仓埋点在流写完后才读供应商请求 id。</p>
 *
 * <p>2026-10-23（计费去兜底）：句柄不再持有 {@code UpstreamRequestAdapter}——
 * 适配器已退出计费链路，只为「提取器没结果时回落」而存在的依赖一并删除。</p>
 */
class UpstreamStreamResponseTest {

    private static final DefaultDataBufferFactory FACTORY = new DefaultDataBufferFactory();

    private static UpstreamStreamResponse stream(Map<String, String> headers, String requestId) {
        return new UpstreamStreamResponse(
                Flux.just(FACTORY.wrap("data: {}\n\n".getBytes(StandardCharsets.UTF_8))),
                MediaType.TEXT_EVENT_STREAM,
                null,
                headers, requestId);
    }

    @Test
    @DisplayName("头快照构造时抓取，流消费完仍可读")
    void snapshotIsReadableAfterBodyConsumed() {
        UpstreamStreamResponse response = stream(Map.of("x-request-id", "req_openai"), "req_openai");

        assertEquals("req_openai", response.upstreamRequestId());
        // 消费完整条流（模拟流式转发结束）
        response.body().blockLast();

        assertEquals("req_openai", response.upstreamRequestId(), "流写完后仍必须能取到供应商请求 id");
        assertEquals("req_openai", response.upstreamHeaders().get("x-request-id"));
    }

    @Test
    @DisplayName("头快照为 null 时降级成空 map，不为 null")
    void nullSnapshotDegradesToEmptyMap() {
        UpstreamStreamResponse response = stream(null, null);

        assertTrue(response.upstreamHeaders().isEmpty());
        assertEquals(null, response.upstreamRequestId());
    }

    @Test
    @DisplayName("头快照不可变")
    void snapshotIsUnmodifiable() {
        UpstreamStreamResponse response = stream(Map.of("x-request-id", "req_openai"), "req_openai");

        assertThrows(UnsupportedOperationException.class,
                () -> response.upstreamHeaders().put("x-request-id", "tampered"));
    }

    @Test
    @DisplayName("响应体为 null 时退化成空流，不是 NPE")
    void nullBodyDegradesToEmptyFlux() {
        UpstreamStreamResponse response = new UpstreamStreamResponse(
                null, MediaType.APPLICATION_JSON, null, null, null);

        assertEquals(0L, response.body().count().block());
    }

    @Test
    @DisplayName("SSE 判定：text/event-stream 才逐行转发")
    void detectsEventStream() {
        assertTrue(stream(null, null).isEventStream(), "text/event-stream 必须判定为 SSE");

        UpstreamStreamResponse json = new UpstreamStreamResponse(Flux.empty(),
                MediaType.APPLICATION_JSON, null, null, null);
        assertFalse(json.isEventStream(), "JSON 响应按字节原样转发");

        UpstreamStreamResponse noType = new UpstreamStreamResponse(Flux.empty(),
                null, null, null, null);
        assertFalse(noType.isEventStream(), "无 Content-Type 时保守按裸流处理");
    }
}
