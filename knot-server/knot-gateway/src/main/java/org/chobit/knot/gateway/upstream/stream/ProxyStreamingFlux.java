package org.chobit.knot.gateway.upstream.stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.protocol.StreamLineAdapter;
import org.chobit.knot.adapter.protocol.UpstreamAdapter;
import org.chobit.knot.adapter.support.SseLineSupport;
import org.chobit.knot.common.constants.AiPayloadFields;
import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.adapter.usage.KnotExtendPayload;
import org.chobit.knot.gateway.runtime.FirstTokenHeaderWriter;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.chobit.knot.common.util.JsonKit;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 把上游响应边收边发给调用方：上游每产出一块就写一块，网关侧不堆积整包响应。
 *
 * <p>2026-10-20（WebFlux 批2）：由 {@code ProxyStreamingBody}（{@code StreamingResponseBody} +
 * {@code InputStream} 逐行读写）改为纯 {@code Flux<DataBuffer>} 变换——
 * 不再有阻塞读、不再有管道、不再需要在 {@code finally} 里手工补抛「泵出错」。</p>
 *
 * <p>SSE 场景额外做一件事：逐事件累计用量，并在上游 {@code data: [DONE]} <b>之前</b>插入独立的
 * {@code knot_extend} 事件（上游原始 usage 以并列 {@code usage} 字段透出；上游不发 [DONE] 时在流结束处
 * 兜底插入），不改动上游原有 chunk，与整包缓冲模式下的行为保持一致。</p>
 */
@Slf4j
public final class ProxyStreamingFlux {

    private static final String DONE_MARKER = "[DONE]";
    private static final String USAGE_FIELD = "usage";
    private static final byte NEW_LINE = (byte) '\n';
    private static final byte[] EMPTY = new byte[0];

    /**
     * 单行累积上限（1MB）：正常 SSE 事件是几十到几千字节，1MB 已经远超合理值。
     * 上游若长时间不发换行（畸形或恶意），累积到上限即失败，避免把网关堆内存吃穿。
     */
    private static final int MAX_LINE_BYTES = 1 << 20;

    private ProxyStreamingFlux() {
    }

    /**
     * 包装上游响应体：首 token 打点、SSE 用量注入、结束收尾。
     *
     * @param stream      上游流式响应句柄
     * @param registry    用量提取器（逐事件解析）
     * @param appendUsage 是否注入 {@code knot_extend}（消费者配置 或 测试头）
     * @param factory     响应侧缓冲区工厂
     * @param writer      首 token 写头器（可为 {@code null}，自动降级）
     * @param usageSink   流结束时的用量出口（数仓事件），可为 {@code null}
     * @param onComplete  流结束收尾（释放在途计数 + 补记用量），可为 {@code null}
     */
    public static Flux<DataBuffer> wrap(UpstreamStreamResponse stream,
                                        UsageExtractorRegistry registry,
                                        boolean appendUsage,
                                        DataBufferFactory factory,
                                        FirstTokenHeaderWriter writer,
                                        Consumer<UsageAccounting> usageSink,
                                        Runnable onComplete) {
        FirstTokenHeaderWriter firstTokenWriter = writer == null ? FirstTokenHeaderWriter.noop() : writer;
        UsageState state = new UsageState();
        // 流式适配器按请求新建：EE 有跨事件状态（chunk id、message_start 只发一次），
        // 而适配器本身是单例 Bean，状态不能挂在那边
        StreamLineAdapter adapter = streamAdapter(stream);
        Flux<DataBuffer> body = stream.isEventStream()
                ? eventStream(stream, adapter, registry, appendUsage, factory, firstTokenWriter, state)
                : rawStream(stream, firstTokenWriter);
        return body
                // 上游中途失败：错误沿 onError 传播（批1 的管道桥接会把它变成静默截断）
                .doOnError(thrown -> log.warn("Upstream stream failed before completion", thrown))
                .doFinally(signal -> {
                    if (usageSink != null) {
                        usageSink.accept(state.usage());
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
    }

    /**
     * 非 SSE：原样转发字节，只做首 token 打点。缓冲区交给下游写出方释放。
     */
    private static Flux<DataBuffer> rawStream(UpstreamStreamResponse stream, FirstTokenHeaderWriter writer) {
        return stream.body().doOnNext(ignored -> writer.mark());
    }

    /**
     * SSE：按行拆分 → 逐行适配（可抑制 / 可合成多行）→ 累计/注入用量 → 重新编码为 {@code DataBuffer}。
     */
    private static Flux<DataBuffer> eventStream(UpstreamStreamResponse stream,
                                                StreamLineAdapter adapter,
                                                UsageExtractorRegistry registry,
                                                boolean appendUsage,
                                                DataBufferFactory factory,
                                                FirstTokenHeaderWriter writer,
                                                UsageState state) {
        Flux<String> outbound = lines(stream.body())
                .concatMap(line -> transform(line, stream, adapter, registry, appendUsage, writer, state))
                // 上游不发 [DONE]（Anthropic 以 event: message_stop 收尾）时在流结束处兜底注入，
                // 否则调用方永远拿不到 knot_extend
                .concatWith(Flux.defer(() -> {
                    if (!appendUsage || state.appended()) {
                        return Flux.empty();
                    }
                    String usageLine = state.usageLine();
                    return usageLine == null ? Flux.empty() : Flux.just(usageLine);
                }));
        return outbound.map(line -> factory.wrap((line + "\n").getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 单行处理：先过适配器（0..N 行），再对适配后的行做 {@code [DONE]} 插入与用量累计。
     *
     * <p>顺序不能反过来：协议转换类适配器会把上游的收尾事件<b>合成</b>成 {@code [DONE]}
     * （Anthropic 的 {@code message_stop} → {@code data: [DONE]}），所以要按适配后的行判断流末尾，
     * 否则 {@code knot_extend} 会插在客户端已经不认的位置。</p>
     *
     * <p>响应适配是协议正确性，不受 {@code appendUsage}（计费注入开关）影响——
     * 该开关只 gate 用量提取与 {@code knot_extend} 注入。</p>
     */
    private static Flux<String> transform(String line,
                                          UpstreamStreamResponse stream,
                                          StreamLineAdapter adapter,
                                          UsageExtractorRegistry registry,
                                          boolean appendUsage,
                                          FirstTokenHeaderWriter writer,
                                          UsageState state) {
        // 首行数据到达即首 token：写出前记录端到端耗时（响应头此刻尚未 commit，仍可写）
        writer.mark();
        List<String> adapted = adaptedLines(adapter, stream, line);
        if (!appendUsage) {
            return Flux.fromIterable(adapted);
        }
        List<String> output = new ArrayList<>(adapted.size() + 1);
        for (String current : adapted) {
            if (!state.appended() && SseLineSupport.isDone(current)) {
                state.appended(true);
                String usageLine = state.usageLine();
                if (usageLine != null) {
                    output.add(usageLine);
                }
            }
            String data = SseLineSupport.dataOf(current);
            if (data != null && !SseLineSupport.isDone(current)) {
                state.merge(extractUsage(stream, registry, data));
            }
            output.add(current);
        }
        return Flux.fromIterable(output);
    }

    /** 单行适配：无适配器 / 适配器返回 {@code null} 时原样透传（零成本路径）。 */
    private static List<String> adaptedLines(StreamLineAdapter adapter, UpstreamStreamResponse stream, String line) {
        if (adapter == null) {
            return List.of(line);
        }
        List<String> adapted = adapter.adapt(line);
        return adapted == null ? List.of(line) : adapted;
    }

    /** 本次请求的流式适配器：无适配器时取透传实现。 */
    private static StreamLineAdapter streamAdapter(UpstreamStreamResponse stream) {
        UpstreamAdapter adapter = stream.adapter();
        if (adapter == null) {
            return StreamLineAdapter.PASSTHROUGH;
        }
        StreamLineAdapter resolved = adapter.streamAdapter(stream.context());
        return resolved == null ? StreamLineAdapter.PASSTHROUGH : resolved;
    }

    /**
     * 把字节流切成「完整行」：跨 chunk 的半行留在 tail 里，只在换行处解码，
     * 保证多字节 UTF-8 字符不会被 chunk 边界劈开。
     */
    static Flux<String> lines(Flux<DataBuffer> body) {
        AtomicReference<byte[]> tail = new AtomicReference<>(EMPTY);
        return body.concatMap(buffer -> {
                    byte[] combined = concat(tail.getAndSet(EMPTY), readAndRelease(buffer));
                    int lastBreak = lastNewLine(combined);
                    if (lastBreak < 0) {
                        if (combined.length > MAX_LINE_BYTES) {
                            return Flux.error(new IllegalStateException(
                                    "upstream SSE line exceeds " + MAX_LINE_BYTES + " bytes without a newline"));
                        }
                        tail.set(combined);
                        return Flux.empty();
                    }
                    tail.set(slice(combined, lastBreak + 1, combined.length));
                    String complete = new String(combined, 0, lastBreak, StandardCharsets.UTF_8);
                    return Flux.fromArray(complete.split("\n", -1));
                })
                // 末尾没有换行的残行也要发出去，否则最后一条事件会丢
                .concatWith(Flux.defer(() -> {
                    byte[] rest = tail.getAndSet(EMPTY);
                    return rest.length == 0 ? Flux.empty() : Flux.just(new String(rest, StandardCharsets.UTF_8));
                }));
    }

    private static byte[] readAndRelease(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return bytes;
    }

    /**
     * 单个 SSE 事件的用量提取。失败不影响转发，只影响最终是否注入用量事件。
     */
    private static UsageAccounting extractUsage(UpstreamStreamResponse stream,
                                                UsageExtractorRegistry registry,
                                                String data) {
        if (registry == null
                || StringUtils.isEmpty(data)
                || DONE_MARKER.equals(data)
                || !data.contains(USAGE_FIELD)) {
            return null;
        }
        return registry.extractEvent(data, stream.context());
    }

    private static byte[] concat(byte[] left, byte[] right) {
        if (left.length == 0) {
            return right;
        }
        byte[] merged = new byte[left.length + right.length];
        System.arraycopy(left, 0, merged, 0, left.length);
        System.arraycopy(right, 0, merged, left.length, right.length);
        return merged;
    }

    private static byte[] slice(byte[] source, int from, int to) {
        if (from >= to) {
            return EMPTY;
        }
        return Arrays.copyOfRange(source, from, to);
    }

    private static int lastNewLine(byte[] bytes) {
        for (int i = bytes.length - 1; i >= 0; i--) {
            if (bytes[i] == NEW_LINE) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 逐事件累计用量与「是否已注入」的可变状态。
     *
     * <p>单次请求内 SSE 是顺序处理的（{@code concatMap}），不存在并发写。</p>
     */
    private static final class UsageState {

        private UsageAccounting value;
        private boolean appended;

        void merge(UsageAccounting candidate) {
            if (candidate == null) {
                return;
            }
            // 整份选择（不是逐字段合并）：取 token 总量更大的一份，相等时后者覆盖前者。
            // 纯媒体事件的 totalTokens 恒 0，会永久输给任一 token>0 的事件 —— 已知遗留，
            // 与 UsageExtractItem.mergeMax 的逐字段口径不同层（见 60-business-rules §8.6）
            if (value == null || candidate.totalTokens() >= value.totalTokens()) {
                value = candidate;
            }
        }

        UsageAccounting usage() {
            return value;
        }

        boolean appended() {
            return appended;
        }

        void appended(boolean flag) {
            this.appended = flag;
        }

        /**
         * 独立的 {@code knot_extend} 事件行（上游原始 usage 以并列 {@code usage} 字段透出，
         * 供下游对照排障）；用量缺失或两视图都为空时返回 {@code null}，不塞空对象给调用方。
         */
        String usageLine() {
            if (value == null) {
                return null;
            }
            KnotExtendPayload payload = KnotExtendPayload.of(value);
            if (payload == null) {
                return null;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            event.put(AiPayloadFields.KNOT_EXTEND, payload);
            Map<String, Object> rawUsage = value.rawUsage();
            if (rawUsage != null && !rawUsage.isEmpty()) {
                event.put(AiPayloadFields.RAW_USAGE, rawUsage);
            }
            return SseLineSupport.data(JsonKit.toJson(event));
        }
    }
}
