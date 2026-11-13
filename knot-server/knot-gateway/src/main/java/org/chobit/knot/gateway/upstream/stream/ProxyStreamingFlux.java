package org.chobit.knot.gateway.upstream.stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.protocol.StreamLineAdapter;
import org.chobit.knot.adapter.protocol.UpstreamAdapter;
import org.chobit.knot.adapter.support.SseLineSupport;
import org.chobit.knot.common.constants.AiPayloadFields;
import org.chobit.knot.common.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.adapter.usage.KnotExtendPayload;
import org.chobit.knot.gateway.runtime.FirstTokenHeaderWriter;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.chobit.knot.common.util.JsonKit;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
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
 *
 * <p>⭐ 正文 tee 是<b>只读旁路</b>：在 {@code lines()} 的 wire 行（非 SSE 为原始字节块）上
 * {@code doOnNext} 复制一份，主链拿到的还是原来那块 {@code DataBuffer} —— 不动 readPosition、
 * 不 release、不额外 {@code request(n)}、不切线程。攒进有界头尾缓冲，流结束时
 * （{@code doFinally}，取消 / 出错同样触发）由 {@code streamBodySink} 写出。
 * 用头尾双留而不是「只留前 N 字节」：usage 与收尾事件都在流末尾，只留头部等于留了个开头。</p>
 */
@Slf4j
public final class ProxyStreamingFlux {

    private static final String DONE_MARKER = "[DONE]";
    private static final String USAGE_FIELD = "usage";
    private static final byte NEW_LINE = (byte) '\n';
    private static final byte[] EMPTY = new byte[0];

    /** 流式正文 tee 的开关：默认开启（日志插件是 opt-in，但启用后必须真的有正文可看） */
    private static final String STREAM_BODY_PROPERTY = "knot.plugin.log.stream.body";

    /** 流式正文 tee 的总字节上限（头尾各一半），属性可配 */
    private static final String STREAM_BODY_MAX_PROPERTY = "knot.plugin.log.stream.body.max-bytes";

    private static final int DEFAULT_STREAM_BODY_MAX_BYTES = 8192;

    private static final String OMITTED_MARK = "\n...(omitted %d bytes)...\n";

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
        return wrap(stream, registry, appendUsage, factory, writer, usageSink, onComplete, null);
    }

    /**
     * @param streamBodySink 流结束时的正文出口（有界 tee 快照，可能为 {@code null}）；
     *                       传 {@code null} 时整条旁路都不挂，零成本。
     *                       生产链路必须传：不传就没有流式的上游响应正文日志。
     */
    public static Flux<DataBuffer> wrap(UpstreamStreamResponse stream,
                                        UsageExtractorRegistry registry,
                                        boolean appendUsage,
                                        DataBufferFactory factory,
                                        FirstTokenHeaderWriter writer,
                                        Consumer<UsageAccounting> usageSink,
                                        Runnable onComplete,
                                        Consumer<String> streamBodySink) {
        FirstTokenHeaderWriter firstTokenWriter = writer == null ? FirstTokenHeaderWriter.noop() : writer;
        UsageState state = new UsageState();
        // 流式适配器按请求新建：EE 有跨事件状态（chunk id、message_start 只发一次），
        // 而适配器本身是单例 Bean，状态不能挂在那边
        StreamLineAdapter adapter = streamAdapter(stream);
        BodyTee tee = streamBodySink == null || !streamBodyEnabled() ? null : new BodyTee(streamBodyMaxBytes() / 2);
        Flux<DataBuffer> body = stream.isEventStream()
                ? eventStream(stream, adapter, registry, appendUsage, factory, firstTokenWriter, state, tee)
                : rawStream(stream, firstTokenWriter, tee);
        return emptyGuard(body)
                // 上游中途失败：错误沿 onError 传播（批1 的管道桥接会把它变成静默截断）
                .doOnError(thrown -> log.warn("Upstream stream failed before completion", thrown))
                .doFinally(signal -> {
                    // 日志是旁路：它自己的异常不能打断已经开始的记账与在途释放
                    if (tee != null) {
                        try {
                            streamBodySink.accept(tee.snapshot());
                        } catch (RuntimeException e) {
                            log.warn("stream body tee log failed", e);
                        }
                    }
                    if (usageSink != null) {
                        usageSink.accept(state.usage());
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
    }

    /**
     * 上游 2xx 却一个字节都没给：绝不能以「正常结束」收场——调用方会以为模型答了空，
     * 计费侧也拿不到用量，两侧同时静默出错。
     *
     * <p>此处响应头早已写出（首行 flush 即提交），改不了状态码，只能让流以错误终止；
     * 能提前发现的零长度响应由执行器在写出前判掉（{@code executeStreaming} 看
     * {@code Content-Length: 0}），本方法是「chunked 但一个字节不发」这条漏网路径的兜底。</p>
     */
    private static Flux<DataBuffer> emptyGuard(Flux<DataBuffer> body) {
        return body.switchOnFirst((signal, flux) -> {
            if (signal.hasValue() || signal.hasError()) {
                return flux;
            }
            return Flux.error(new GatewayUpstreamException(
                    "upstream stream ended without any body",
                    ProxyErrorCodeEnum.UPSTREAM_EMPTY_RESPONSE.code()));
        });
    }

    /**
     * 非 SSE：原样转发字节，只做首 token 打点。缓冲区交给下游写出方释放。
     */
    private static Flux<DataBuffer> rawStream(UpstreamStreamResponse stream,
                                              FirstTokenHeaderWriter writer,
                                              BodyTee tee) {
        return stream.body().doOnNext(buffer -> {
            writer.mark();
            if (tee != null) {
                tee.append(readOnlyBytes(buffer));
            }
        });
    }

    /**
     * 旁路只读拷贝：{@code asByteBuffer()} 是可读区视图，duplicate 后再读，
     * 主链的 readPosition 与引用计数都不动。绝不能在这里 {@code read()} / {@code release()}。
     */
    private static byte[] readOnlyBytes(DataBuffer buffer) {
        ByteBuffer view = buffer.asByteBuffer().duplicate();
        byte[] bytes = new byte[view.remaining()];
        view.get(bytes);
        return bytes;
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
                                                UsageState state,
                                                BodyTee tee) {
        Flux<String> inbound = lines(stream.body());
        if (tee != null) {
            // tee 挂在适配之前的 wire 行上：适配后的行里字段名已是网关重建的，不是供应商原话
            inbound = inbound.doOnNext(tee::appendLine);
        }
        Flux<String> outbound = inbound
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
        // 证据侧取未适配的行：适配后的载荷里 usage 已是网关重建的键名，拿去对账等于对自家转换结果
        String wireData = SseLineSupport.dataOf(line);
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
                state.latest(extractUsage(stream, registry, data, wireData));
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
                            return lineTooLong();
                        }
                        tail.set(combined);
                        return Flux.empty();
                    }
                    // 逐段量长度：只在「整个 chunk 没有换行」时判长度是不够的——
                    // 一个含换行但单行超限的大 chunk 会先构造上兆的 String 再 split，
                    // 长度保护等于没生效。这里只扫字节、不建串，超限立刻失败。
                    int lineStart = 0;
                    for (int i = 0; i <= lastBreak; i++) {
                        if (combined[i] == '\n') {
                            if (i - lineStart > MAX_LINE_BYTES) {
                                return lineTooLong();
                            }
                            lineStart = i + 1;
                        }
                    }
                    byte[] rest = slice(combined, lastBreak + 1, combined.length);
                    if (rest.length > MAX_LINE_BYTES) {
                        return lineTooLong();
                    }
                    tail.set(rest);
                    String complete = new String(combined, 0, lastBreak, StandardCharsets.UTF_8);
                    return Flux.fromArray(complete.split("\n", -1));
                })
                // 末尾没有换行的残行也要发出去，否则最后一条事件会丢
                .concatWith(Flux.defer(() -> {
                    byte[] rest = tail.getAndSet(EMPTY);
                    return rest.length == 0 ? Flux.empty() : Flux.just(new String(rest, StandardCharsets.UTF_8));
                }));
    }

    /**
     * 单行超过 {@link #MAX_LINE_BYTES}：畸形上游或异常代理才会出现，正常 SSE 事件远小于此。
     *
     * <p>以错误终止而非截断：截断会让客户端把半条 JSON 当成完整事件解析，
     * 错误至少能被上层看见并计入上游失败。</p>
     */
    private static Flux<String> lineTooLong() {
        return Flux.error(new IllegalStateException(
                "upstream SSE line exceeds " + MAX_LINE_BYTES + " bytes"));
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
                                                String data,
                                                String wireData) {
        if (registry == null
                || StringUtils.isEmpty(data)
                || DONE_MARKER.equals(data)
                || !data.contains(USAGE_FIELD)) {
            return null;
        }
        return registry.extractEvent(data, wireData, stream.context());
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

    private static boolean streamBodyEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(STREAM_BODY_PROPERTY, "true").trim());
    }

    /** 上限解析：非法 / 过小一律回落默认值，与日志正文预算的「宁可少落」同口径。 */
    private static int streamBodyMaxBytes() {
        String raw = System.getProperty(STREAM_BODY_MAX_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_STREAM_BODY_MAX_BYTES;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 1 ? parsed : DEFAULT_STREAM_BODY_MAX_BYTES;
        } catch (NumberFormatException e) {
            return DEFAULT_STREAM_BODY_MAX_BYTES;
        }
    }

    /**
     * 有界头尾旁路：头部填满后，余下字节只进尾部环形缓冲，头尾<b>不重叠</b>。
     *
     * <p>⚠ 输出是 UTF-8 解码的字符串：头尾切点可能正好劈开一个多字节字符，解码会得到
     * replacement char。作为排障 / 对账快照可接受——它不是要回放到客户端的正文。</p>
     *
     * <p>{@code synchronized} 不是为了并发写（Reactor 逐块串行），而是给
     * {@code doFinally} 可能落在另一线程时提供可见性；无竞争，开销可忽略。</p>
     */
    static final class BodyTee {

        private final byte[] head;
        private final byte[] tail;
        private int headLength;
        private int tailLength;
        private int tailStart;
        private long total;

        /** @param sideBytes 头 / 尾各保留的字节数 */
        BodyTee(int sideBytes) {
            this.head = new byte[Math.max(1, sideBytes)];
            this.tail = new byte[Math.max(1, sideBytes)];
        }

        synchronized void appendLine(String line) {
            append((line + "\n").getBytes(StandardCharsets.UTF_8));
        }

        synchronized void append(byte[] bytes) {
            total += bytes.length;
            int offset = 0;
            if (headLength < head.length) {
                int n = Math.min(bytes.length, head.length - headLength);
                System.arraycopy(bytes, 0, head, headLength, n);
                headLength += n;
                offset = n;
            }
            // 头部之后的字节才进尾环：头尾区间不重叠，省略量才不会算成负数
            for (int i = offset; i < bytes.length; i++) {
                if (tailLength < tail.length) {
                    tail[(tailStart + tailLength) % tail.length] = bytes[i];
                    tailLength++;
                } else {
                    tail[tailStart] = bytes[i];
                    tailStart = (tailStart + 1) % tail.length;
                }
            }
        }

        /** @return 一个字节都没收到时 {@code null}：让调用方区分「空流」与「空字符串」 */
        synchronized String snapshot() {
            if (total == 0) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(headLength + tailLength + 64);
            out.write(head, 0, headLength);
            long omitted = total - headLength - tailLength;
            if (omitted > 0) {
                out.writeBytes(String.format(OMITTED_MARK, omitted).getBytes(StandardCharsets.UTF_8));
            }
            for (int i = 0; i < tailLength; i++) {
                out.write(tail[(tailStart + i) % tail.length]);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * 逐事件累计用量与「是否已注入」的可变状态。
     *
     * <p>单次请求内 SSE 是顺序处理的（{@code concatMap}），不存在并发写。</p>
     */
    private static final class UsageState {

        private UsageAccounting value;
        private boolean appended;

        /**
         * 后到覆盖前一个（2026-11-13 robin 定：上游 usage 通常在最后一个事件）。
         *
         * <p>⭐ <b>代价是明知的，不是没想到</b>：Anthropic 原生 {@code messages} 流式把用量拆在两个事件里 ——
         * {@code message_start.usage.input_tokens} 与 {@code message_delta.usage.output_tokens}，
         * 只留最后一个事件时<b>输入侧 token 记 0</b>。走适配器的协议不受影响：
         * {@code AnthropicStreamSupport} 把整轮用量合进最后一个 chunk 的 {@code usage}（含
         * {@code prompt_tokens}），最后一个事件本身就是全量。</p>
         *
         * <p>此前是「取 token 总量更大的一份」：同一场景下也只是二选一，并非逐字段合并，
         * 且谁大谁赢会让结果随请求内容跳变，比固定取最后一个更难解释。逐字段合并
         * （{@code UsageExtractItem.mergeMax}）只存在于提取器内部，不在这一层。</p>
         */
        void latest(UsageAccounting candidate) {
            if (candidate != null) {
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
