package org.chobit.knot.gateway.upstream.stream;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.protocol.StreamLineAdapter;
import org.chobit.knot.gateway.adapter.protocol.UpstreamAdapter;
import org.chobit.knot.gateway.adapter.support.SseLineSupport;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.config.UsageExtractionProperties;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.gateway.adapter.model.usage.UsageAccounting;
import org.chobit.knot.gateway.runtime.FirstTokenHeaderWriter;
import org.chobit.knot.gateway.runtime.RecordingHttpExchange;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.chobit.knot.gateway.adapter.usage.ModelUsageNormalizer;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
 *
 * <p>2026-10-25（流式响应适配）：SSE 行一律经 {@code UpstreamAdapter.streamAdapter} 转换后再转发与提取
 * （默认透传零变化），与整包路径的 {@code handleResponse} 同构；适配器可抑制行、也可把一行合成多行。</p>
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

    @Test
    @DisplayName("流式事件经适配器转换后转发，用量提取吃转换后的数据")
    void adaptsStreamEventBeforeForwardAndExtraction() {
        AtomicReference<String> extracted = new AtomicReference<>();
        String payload = "data: {\"choices\":[],\"usage\":{\"inputTokens\":10,\"output_tokens\":5}}\n"
                + "data: [DONE]\n";
        // camelCase 别名转 snake_case 的 stub：模拟「上游流式口径与网关不同」的适配器
        UpstreamAdapter adapter = new UpstreamAdapter() {
            @Override
            public String code() {
                return "STUB_CAMEL_TO_SNAKE";
            }

            @Override
            public String label() {
                return "stub";
            }

            @Override
            public Set<org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum> protocol() {
                return Set.of(org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum.CHAT_COMPLETIONS);
            }

            @Override
            public Object buildRequestBody(UpstreamRequestContext ctx) {
                return ctx.requestBody();
            }

            @Override
            public StreamLineAdapter streamAdapter(UpstreamRequestContext ctx) {
                return line -> {
                    String data = SseLineSupport.dataOf(line);
                    if (data == null) {
                        return List.of(line);
                    }
                    String adapted = data.replace("inputTokens", "input_tokens");
                    return adapted.equals(data) ? List.of(line) : List.of(SseLineSupport.data(adapted));
                };
            }
        };
        UsageExtractorRegistry registry = new UsageExtractorRegistry(null, null, new UsageExtractionProperties()) {
            @Override
            public UsageAccounting extractEvent(String eventData, UpstreamRequestContext context) {
                extracted.set(eventData);
                return null;
            }
        };

        String out = collect(ProxyStreamingFlux.wrap(stream(payload, true, adapter), registry, true, FACTORY,
                null, null, null));

        assertTrue(out.contains("\"input_tokens\":10"), "客户端必须收到转换后的事件，实际：" + out);
        assertFalse(out.contains("inputTokens"), "上游 camelCase 不应出现在转发数据里");
        assertEquals("{\"choices\":[],\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}", extracted.get(),
                "用量提取必须吃转换后的数据（与整包路径同构）");
    }

    @Test
    @DisplayName("响应适配不受 appendUsage 开关影响（协议正确性不依赖计费注入）")
    void adaptsStreamEventEvenWithoutUsageInjection() {
        String payload = "data: {\"text\":\"hi\"}\n";
        UpstreamAdapter upper = new UpstreamAdapter() {
            @Override
            public String code() {
                return "STUB_UPPER";
            }

            @Override
            public String label() {
                return "stub";
            }

            @Override
            public Set<org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum> protocol() {
                return Set.of(org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum.CHAT_COMPLETIONS);
            }

            @Override
            public Object buildRequestBody(UpstreamRequestContext ctx) {
                return ctx.requestBody();
            }

            @Override
            public StreamLineAdapter streamAdapter(UpstreamRequestContext ctx) {
                return line -> {
                    String data = SseLineSupport.dataOf(line);
                    if (data == null) {
                        return List.of(line);
                    }
                    String adapted = data.replace("\"hi\"", "\"HI\"");
                    return adapted.equals(data) ? List.of(line) : List.of(SseLineSupport.data(adapted));
                };
            }
        };

        String out = collect(ProxyStreamingFlux.wrap(stream(payload, true, upper), null, false, FACTORY,
                null, null, null));

        assertTrue(out.contains("\"HI\""), "appendUsage=false 时转换仍须生效，实际：" + out);
    }

    @Test
    @DisplayName("适配器可抑制行（event: 行不下发，空行仍保留作分隔符）")
    void adapterCanSuppressLines() {
        String payload = "event: message_start\ndata: {\"type\":\"message_start\"}\n\n";
        UpstreamAdapter suppressing = stubAdapter(ctx -> line -> {
            if (SseLineSupport.isEvent(line)) {
                return List.of();
            }
            if (StringUtils.isBlank(line)) {
                return List.of(SseLineSupport.BLANK);
            }
            return List.of(SseLineSupport.data("{\"type\":\"converted\"}"));
        });

        String out = collect(ProxyStreamingFlux.wrap(stream(payload, true, suppressing), null, false, FACTORY,
                null, null, null));

        assertFalse(out.contains("event:"), "被抑制的行不应出现在下游，实际：" + out);
        assertTrue(out.contains("data: {\"type\":\"converted\"}"), "其余行照常转换，实际：" + out);
        assertTrue(out.contains("\n\n"), "空行是 SSE 事件分隔符，必须保留，实际：" + out);
    }

    @Test
    @DisplayName("适配器可把一行合成多行（data 行 + 配套 event 行）")
    void adapterCanExpandLineIntoMultiple() {
        String payload = "data: {\"text\":\"hi\"}\n";
        UpstreamAdapter expanding = stubAdapter(ctx -> line -> {
            String data = SseLineSupport.dataOf(line);
            if (data == null) {
                return List.of(line);
            }
            return List.of(SseLineSupport.event("content_block_delta"), SseLineSupport.data(data),
                    SseLineSupport.BLANK);
        });

        String out = collect(ProxyStreamingFlux.wrap(stream(payload, true, expanding), null, false, FACTORY,
                null, null, null));

        assertEquals("event: content_block_delta\ndata: {\"text\":\"hi\"}\n\n", out,
                "一行应合成出 event 行 + data 行 + 分隔空行");
    }

    @Test
    @DisplayName("适配器合成的 [DONE] 同样触发 knot_extend 插在其之前")
    void injectsUsageBeforeSynthesizedDone() {
        // 上游是 Anthropic 形状（收尾是 message_stop，没有 [DONE]），由适配器合成 [DONE]；
        // 用量照 Anthropic 的位置：input 在 message_start、output 在 message_delta
        String payload = "event: message_start\n"
                + "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\","
                + "\"usage\":{\"input_tokens\":10}}}\n"
                + "\n"
                + "event: message_delta\n"
                + "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":5}}\n"
                + "\n"
                + "event: message_stop\n"
                + "data: {\"type\":\"message_stop\"}\n"
                + "\n";
        UpstreamAdapter synthesizing = stubAdapter(ctx -> line -> {
            String data = SseLineSupport.dataOf(line);
            if (data == null) {
                return SseLineSupport.isEvent(line) ? List.of() : List.of(line);
            }
            if (data.contains("\"message_stop\"")) {
                return List.of(SseLineSupport.data(SseLineSupport.doneMarker()));
            }
            return List.of(line);
        });

        String out = collect(ProxyStreamingFlux.wrap(stream(payload, true, synthesizing), stubRegistry(), true,
                FACTORY, null, null, null));
        List<String> lines = out.lines().toList();

        assertEquals(1, lines.stream().filter(line -> line.startsWith("data: {\"knot_extend\"")).count(),
                "knot_extend 只注入一次，实际：" + out);
        String extendEvent = lines.stream()
                .filter(line -> line.startsWith("data: {\"knot_extend\"")).findFirst().orElseThrow();
        assertEquals("data: [DONE]", lines.get(lines.indexOf(extendEvent) + 1),
                "插入位置必须按适配后的 [DONE] 判断，否则客户端已不认这个位置");
    }

    @Test
    @DisplayName("流式适配器按请求新建：跨请求状态互不污染")
    void createsAdapterPerRequest() {
        AtomicInteger created = new AtomicInteger();
        UpstreamAdapter stateful = new UpstreamAdapter() {
            @Override
            public String code() {
                return "STUB_STATEFUL";
            }

            @Override
            public String label() {
                return "stub";
            }

            @Override
            public Set<org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum> protocol() {
                return Set.of(org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum.CHAT_COMPLETIONS);
            }

            @Override
            public Object buildRequestBody(UpstreamRequestContext ctx) {
                return ctx.requestBody();
            }

            @Override
            public StreamLineAdapter streamAdapter(UpstreamRequestContext ctx) {
                created.incrementAndGet();
                // 有状态：首行标记为 first，其余为 next
                AtomicReference<Boolean> seen = new AtomicReference<>(false);
                return line -> {
                    if (StringUtils.isBlank(line)) {
                        return List.of(SseLineSupport.BLANK);
                    }
                    boolean first = seen.compareAndSet(false, true);
                    return List.of(SseLineSupport.data((first ? "first" : "next") + ":" + SseLineSupport.dataOf(line)));
                };
            }
        };
        String payload = "data: a\ndata: b\n";

        String firstRun = collect(ProxyStreamingFlux.wrap(stream(payload, true, stateful), null, false, FACTORY,
                null, null, null));
        String secondRun = collect(ProxyStreamingFlux.wrap(stream(payload, true, stateful), null, false, FACTORY,
                null, null, null));

        assertEquals(2, created.get(), "每次请求都必须新建适配器实例");
        assertEquals("data: first:a\ndata: next:b\n", firstRun,
                "首次请求必须从 fresh 状态开始（实际=" + firstRun.replace("\n", "|") + "）");
        assertEquals(firstRun, secondRun, "第二次请求必须从 fresh 状态开始，不能被上一请求污染");
    }

    private static UpstreamAdapter stubAdapter(
            java.util.function.Function<UpstreamRequestContext, StreamLineAdapter> factory) {
        return new UpstreamAdapter() {
            @Override
            public String code() {
                return "STUB_STREAM";
            }

            @Override
            public String label() {
                return "stub";
            }

            @Override
            public Set<org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum> protocol() {
                return Set.of(org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum.CHAT_COMPLETIONS);
            }

            @Override
            public Object buildRequestBody(UpstreamRequestContext ctx) {
                return ctx.requestBody();
            }

            @Override
            public StreamLineAdapter streamAdapter(UpstreamRequestContext ctx) {
                return factory.apply(ctx);
            }
        };
    }

    private static String collect(Flux<DataBuffer> body) {        return body.map(buffer -> buffer.toString(StandardCharsets.UTF_8))
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

    private static UpstreamStreamResponse stream(String payload, boolean eventStream, UpstreamAdapter adapter) {
        return new UpstreamStreamResponse(
                Flux.just(FACTORY.wrap(payload.getBytes(StandardCharsets.UTF_8))),
                eventStream ? MediaType.TEXT_EVENT_STREAM : MediaType.APPLICATION_JSON,
                null, adapter, null, null);
    }

    private static UsageExtractorRegistry stubRegistry() {
        return new UsageExtractorRegistry(null, null, new UsageExtractionProperties()) {
            @Override
            public UsageAccounting extractEvent(String eventData,
                                                UpstreamRequestContext context) {
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
