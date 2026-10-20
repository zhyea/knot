package org.chobit.knot.gateway.upstream.stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.model.UsageAccounting;
import org.chobit.knot.gateway.model.usage.KnotExtendPayload;
import org.chobit.knot.gateway.runtime.FirstTokenHeaderWriter;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
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

    private static final String DATA_PREFIX = "data:";
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
        Flux<DataBuffer> body = stream.isEventStream()
                ? eventStream(stream, registry, appendUsage, factory, firstTokenWriter, state)
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
     * SSE：按行拆分 → 逐行累计/注入用量 → 重新编码为 {@code DataBuffer}。
     */
    private static Flux<DataBuffer> eventStream(UpstreamStreamResponse stream,
                                                UsageExtractorRegistry registry,
                                                boolean appendUsage,
                                                DataBufferFactory factory,
                                                FirstTokenHeaderWriter writer,
                                                UsageState state) {
        Flux<String> outbound = lines(stream.body())
                .concatMap(line -> transform(line, stream, registry, appendUsage, writer, state))
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
     * 单行处理：{@code [DONE]} 之前插入用量事件，其余行原样透传（同时累计用量）。
     */
    private static Flux<String> transform(String line,
                                          UpstreamStreamResponse stream,
                                          UsageExtractorRegistry registry,
                                          boolean appendUsage,
                                          FirstTokenHeaderWriter writer,
                                          UsageState state) {
        // 首行数据到达即首 token：写出前记录端到端耗时（响应头此刻尚未 commit，仍可写）
        writer.mark();
        String trimmed = StringUtils.trim(line);
        if (appendUsage && !state.appended() && isDoneEvent(trimmed)) {
            state.appended(true);
            String usageLine = state.usageLine();
            return usageLine == null ? Flux.just(line) : Flux.just(usageLine, line);
        }
        if (appendUsage && trimmed.startsWith(DATA_PREFIX)) {
            state.merge(extractUsage(stream, registry, StringUtils.trim(trimmed.substring(DATA_PREFIX.length()))));
        }
        return Flux.just(line);
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

    private static boolean isDoneEvent(String trimmedLine) {
        if (!trimmedLine.startsWith(DATA_PREFIX)) {
            return false;
        }
        return DONE_MARKER.equals(StringUtils.trim(trimmedLine.substring(DATA_PREFIX.length())));
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
        return registry.extractEvent(data, stream.context(), stream.adapter());
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
            // 逐事件取 token 数更大的一份，与整包提取的 mergeMax 语义对齐
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
            return DATA_PREFIX + " " + JsonKit.toJson(event);
        }
    }
}
