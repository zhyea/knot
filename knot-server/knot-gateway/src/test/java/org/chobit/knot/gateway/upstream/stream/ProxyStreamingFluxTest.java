package org.chobit.knot.gateway.upstream.stream;

import org.chobit.knot.gateway.adapter.request.UpstreamRequestAdapter;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.model.UsageAccounting;
import org.chobit.knot.gateway.runtime.FirstTokenHeaderWriter;
import org.chobit.knot.gateway.runtime.RecordingHttpExchange;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.chobit.knot.gateway.usage.ModelUsageNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式转发的响应体变换契约（{@link ProxyStreamingFlux}，替代已删除的 {@code ProxyStreamingBody}）。
 *
 * <p>2026-10-20（WebFlux 批2）：不再有 InputStream / 管道 / {@code writeTo}，断言方式改为
 * 把 {@code Flux<DataBuffer>} 收集成字符串。重点四条不变量：</p>
 * <ol>
 *   <li>在途计数由<b>流结束</b>释放（含上游中断 / 客户端取消）；</li>
 *   <li>{@code knot_extend} 只注入一次，且在 {@code [DONE]} 之前（无 DONE 时兜底在末尾）；</li>
 *   <li>上游中断<b>沿 onError 传播</b>——批1 的管道桥接会把它退化成静默截断；</li>
 *   <li>跨 chunk 的多字节字符不能被劈开（行切分只在换行处解码）。</li>
 * </ol>
 */
class ProxyStreamingFluxTest {

    private static final DataBufferFactory FACTORY = new DefaultDataBufferFactory();

    @Test
    @DisplayName("SSE 流写完释放一次在途计数")
    void releasesInflightAfterEventStreamFinished() {
        AtomicInteger released = new AtomicInteger();
        String payload = "data: {\"choices\":[]}\ndata: [DONE]\n";

        String out = collect(ProxyStreamingFlux.wrap(stream(payload, true), null, false, FACTORY, null,
                null, released::incrementAndGet));

        assertTrue(out.contains("[DONE]"), out);
        assertEquals(1, released.get(), "SSE 流写完必须释放一次在途计数");
    }

    @Test
    @DisplayName("裸流（非 SSE）写完同样释放一次")
    void releasesInflightAfterRawStreamFinished() {
        AtomicInteger released = new AtomicInteger();

        String out = collect(ProxyStreamingFlux.wrap(stream("hello-stream", false), null, false, FACTORY,
                null, null, released::incrementAndGet));

        assertEquals("hello-stream", out);
        assertEquals(1, released.get());
    }

    @Test
    @DisplayName("上游中断也必须释放，且错误必须冒泡（不能变成静默截断）")
    void surfacesUpstreamAbortAndStillReleases() {
        AtomicInteger released = new AtomicInteger();
        UpstreamStreamResponse failing = new UpstreamStreamResponse(
                Flux.error(new IOException("upstream reset")), MediaType.TEXT_EVENT_STREAM, null, null, null, null);

        Flux<DataBuffer> body = ProxyStreamingFlux.wrap(failing, null, false, FACTORY, null, null,
                released::incrementAndGet);

        // Reactor 会把受检异常包成 ReactiveException，拆壳后必须仍是原始 IOException
        RuntimeException propagated = assertThrows(RuntimeException.class, body::blockLast,
                "上游中断必须冒泡，不能让客户端拿到半截却无任何信号");
        assertInstanceOf(IOException.class, Exceptions.unwrap(propagated));
        assertEquals(1, released.get(), "中途失败也必须释放，否则在途计数泄漏");
    }

    @Test
    @DisplayName("全 null 收尾回调时不抛 NPE")
    void toleratesMissingCallbacks() {
        String out = collect(ProxyStreamingFlux.wrap(stream("x", false), null, false, FACTORY, null, null, null));
        assertEquals("x", out);
    }

    @Test
    @DisplayName("knot_extend 在 [DONE] 之前只注入一次，上游 chunk 原样透传")
    void appendsKnotExtendEventBeforeDone() {
        String payload = "data: {\"choices\":[],\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}\n"
                + "\n"
                + "data: {\"choices\":[],\"usage\":{\"input_tokens\":20,\"output_tokens\":7}}\n"
                + "\n"
                + "data: [DONE]\n";

        String out = collect(ProxyStreamingFlux.wrap(stream(payload, true), stubRegistry(), true, FACTORY,
                null, null, null));
        List<String> lines = out.lines().toList();

        assertEquals(1, lines.stream().filter(line -> line.startsWith("data: {\"knot_extend\"")).count(),
                "knot_extend 独立事件只注入一次");
        String extendEvent = lines.stream()
                .filter(line -> line.startsWith("data: {\"knot_extend\"")).findFirst().orElseThrow();
        assertTrue(extendEvent.contains(",\"usage\":{"), "上游原始 usage 应以并列字段 usage 注入");
        assertEquals("data: [DONE]", lines.get(lines.indexOf(extendEvent) + 1), "knot_extend 应插在 [DONE] 之前");
        assertTrue(out.contains("data: {\"choices\":[],\"usage\":{\"input_tokens\":20,\"output_tokens\":7}}"),
                "上游原有 chunk 不应被改动");
    }

    @Test
    @DisplayName("上游不发 [DONE] 时 knot_extend 兜底追加在流末尾")
    void appendsKnotExtendEventAtEndWithoutDone() {
        String payload = "data: {\"choices\":[],\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}\n"
                + "\n"
                + "event: message_stop\n"
                + "data: {\"type\":\"message_stop\",\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}\n"
                + "\n";

        String out = collect(ProxyStreamingFlux.wrap(stream(payload, true), stubRegistry(), true, FACTORY,
                null, null, null));
        List<String> lines = out.lines().toList();

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("data: {\"type\":\"message_stop\"")),
                "无 [DONE] 时原有事件原样透传");
        assertTrue(lines.get(lines.size() - 1).startsWith("data: {\"knot_extend\""),
                "无 [DONE] 时 knot_extend 兜底追加在流末尾");
    }

    @Test
    @DisplayName("不可解析的上游 chunk 原样透传，knot_extend 照常注入")
    void passesOriginalChunksThroughUntouched() {
        String payload = "data: {\"choices\":[],\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}\n"
                + "\n"
                + "data: not-json-at-all\n"
                + "\n"
                + "data: [DONE]\n";

        String out = collect(ProxyStreamingFlux.wrap(stream(payload, true), stubRegistry(), true, FACTORY,
                null, null, null));

        assertTrue(out.contains("data: not-json-at-all"), "不可解析的上游 chunk 也应原样透传");
        assertTrue(out.lines().anyMatch(line -> line.startsWith("data: {\"knot_extend\"")),
                "knot_extend 事件照常注入，不依赖对上游 chunk 的解析");
    }

    @Test
    @DisplayName("跨 chunk 的多字节字符不被劈开")
    void keepsMultibyteCharacterAcrossChunkBoundary() {
        // "中文" 的 UTF-8 是 6 字节，切成 4 + 2 两段：第一段结束在多字节字符中间
        byte[] bytes = "data: 中文\n".getBytes(StandardCharsets.UTF_8);
        byte[] head = Arrays.copyOfRange(bytes, 0, 4);
        byte[] tail = Arrays.copyOfRange(bytes, 4, bytes.length);
        Flux<DataBuffer> chunks = Flux.<DataBuffer>just(FACTORY.wrap(head), FACTORY.wrap(tail));
        UpstreamStreamResponse stream = new UpstreamStreamResponse(chunks, MediaType.TEXT_EVENT_STREAM,
                null, null, null, null);

        String out = collect(ProxyStreamingFlux.wrap(stream, null, false, FACTORY, null, null, null));

        assertEquals("data: 中文\n", out, "行切分只在换行处解码，字符不能被 chunk 边界劈开");
    }

    @Test
    @DisplayName("流式响应写首 token 时间头（裸流）")
    void writesFirstTokenTimeHeaderOnRawStream() {
        RecordingHttpExchange carrier = new RecordingHttpExchange();
        FirstTokenHeaderWriter writer = new FirstTokenHeaderWriter(System.currentTimeMillis(), carrier, null);
        UpstreamStreamResponse slow = new UpstreamStreamResponse(
                Flux.just(FACTORY.wrap("hello".getBytes(StandardCharsets.UTF_8)))
                        .delayElements(Duration.ofMillis(100)),
                MediaType.APPLICATION_JSON, null, null, null, null);

        collect(ProxyStreamingFlux.wrap(slow, null, false, FACTORY, writer, null, null));

        String ttft = carrier.responseHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME);
        assertNotNull(ttft, "非 SSE 裸流同样必须写首 token 响应时间头");
        assertTrue(Long.parseLong(ttft) >= 100, "实际 " + ttft + "ms");
    }

    @Test
    @DisplayName("SSE 首行到达即写首 token 头，且只写一次")
    void writesFirstTokenTimeHeaderOnEventStream() {
        RecordingHttpExchange carrier = new RecordingHttpExchange();
        FirstTokenHeaderWriter writer = new FirstTokenHeaderWriter(System.currentTimeMillis(), carrier, null);
        UpstreamStreamResponse slow = new UpstreamStreamResponse(
                Flux.just(FACTORY.wrap("data: a\ndata: [DONE]\n".getBytes(StandardCharsets.UTF_8)))
                        .delayElements(Duration.ofMillis(120)),
                MediaType.TEXT_EVENT_STREAM, null, null, null, null);

        collect(ProxyStreamingFlux.wrap(slow, null, false, FACTORY, writer, null, null));

        String ttft = carrier.responseHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME);
        assertNotNull(ttft, "SSE 首行到达必须写首 token 响应时间头");
        assertTrue(Long.parseLong(ttft) >= 120, "端到端口径应覆盖上游首行延迟，实际 " + ttft + "ms");
    }

    @Test
    @DisplayName("用量出口拿到逐事件累计后的最大一份")
    void usageSinkReceivesAccumulatedUsage() {
        AtomicInteger sinkCalls = new AtomicInteger();
        UsageAccounting[] captured = new UsageAccounting[1];
        String payload = "data: {\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}\n"
                + "data: {\"usage\":{\"input_tokens\":30,\"output_tokens\":9}}\n"
                + "data: [DONE]\n";

        collect(ProxyStreamingFlux.wrap(stream(payload, true), stubRegistry(), true, FACTORY, null,
                accounting -> {
                    sinkCalls.incrementAndGet();
                    captured[0] = accounting;
                }, null));

        assertEquals(1, sinkCalls.get(), "用量出口只在流结束触发一次");
        assertNotNull(captured[0]);
        assertTrue(captured[0].totalTokens() > 0L, "必须累计到实际 token 数");
    }

    @Test
    @DisplayName("无 knot_extend 需求时不做行切分，字节原样透传")
    void rawEventStreamPassesThroughWhenNoUsageInjection() {
        String payload = "data: a\ndata: b\n";

        String out = collect(ProxyStreamingFlux.wrap(stream(payload, true), null, false, FACTORY, null, null, null));

        assertEquals(payload, out);
        assertFalse(out.contains("knot_extend"));
    }

    private static String collect(Flux<DataBuffer> body) {
        return body.map(buffer -> buffer.toString(StandardCharsets.UTF_8))
                .collectList()
                .map(parts -> String.join("", parts))
                .block();
    }

    private static UpstreamStreamResponse stream(String payload, boolean eventStream) {
        return new UpstreamStreamResponse(
                Flux.just(FACTORY.wrap(payload.getBytes(StandardCharsets.UTF_8))),
                eventStream ? MediaType.TEXT_EVENT_STREAM : MediaType.APPLICATION_JSON,
                null, null, null, null);
    }

    private static UsageExtractorRegistry stubRegistry() {
        return new UsageExtractorRegistry(null, null) {
            @Override
            public UsageAccounting extractEvent(String eventData,
                                                UpstreamRequestContext context,
                                                UpstreamRequestAdapter adapter) {
                // 按事件内容返回不同 token 数，用于验证「取更大一份」的累计口径
                int input = eventData.contains("_tokens\":30") ? 30 : 10;
                int output = eventData.contains("output_tokens\":9") ? 9 : 5;
                Map<String, Object> usage = Map.of("input_tokens", input, "output_tokens", output);
                TokenBillingUsage billing = TokenBillingUsage.from(usage);
                // 双视图：提取器必须同时产出 usage 视图，否则 knot_extend 无法注入
                return UsageAccounting.of(usage, Map.of("usage", usage), billing,
                        ModelUsageNormalizer.fromBilling(billing, Map.of("usage", usage)), null);
            }
        };
    }
}
