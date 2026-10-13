package org.chobit.knot.gateway.upstream.stream;

import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.model.UsageAccounting;
import org.chobit.knot.gateway.model.usage.KnotExtendPayload;
import org.chobit.knot.gateway.runtime.FirstTokenHeaderWriter;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把上游响应边收边发给调用方：读完一行立刻写出并 flush，不在网关侧堆积整包响应。
 *
 * <p>SSE 场景额外做一件事：逐事件累计用量，并在上游 {@code data: [DONE]} 之前插入独立的
 * {@code knot_extend} 事件（上游原始 usage 以并列 {@code usage} 字段透出；上游不发 [DONE] 时在流结束处兜底插入），
 * 不改动上游原有 chunk，与整包缓冲模式下的行为保持一致。</p>
 */
public class ProxyStreamingBody implements StreamingResponseBody {

    private static final String DATA_PREFIX = "data:";
    private static final String DONE_MARKER = "[DONE]";
    private static final String USAGE_FIELD = "usage";

    private final UpstreamStreamResponse stream;
    private final UsageExtractorRegistry usageExtractorRegistry;
    private final boolean appendUsage;
    private final int bufferSize;
    /**
     * 流结束（正常写完或中途抛错/客户端断连）时执行的收尾回调。
     * 用于释放本次请求的模型在途计数——流式请求在首字节后请求线程就已返回，
     * 在途必须覆盖到流写完，否则长流式响应会被当成「已空闲」而吸走超额流量。
     */
    private final Runnable onComplete;
    /**
     * 首 token 响应时间写头器：上游首行数据到达时记录端到端耗时。
     * 构造时把 {@code null} 归一为降级实例，调用点无需判空。
     */
    private final FirstTokenHeaderWriter firstTokenWriter;

    public ProxyStreamingBody(UpstreamStreamResponse stream,
                              UsageExtractorRegistry usageExtractorRegistry,
                              boolean appendUsage,
                              int bufferSize,
                              Runnable onComplete,
                              FirstTokenHeaderWriter firstTokenWriter) {
        this.stream = stream;
        this.usageExtractorRegistry = usageExtractorRegistry;
        this.appendUsage = appendUsage;
        this.bufferSize = bufferSize > 0 ? bufferSize : 8192;
        this.onComplete = onComplete;
        this.firstTokenWriter = firstTokenWriter == null ? FirstTokenHeaderWriter.noop() : firstTokenWriter;
    }

    @Override
    public void writeTo(@NonNull OutputStream outputStream) throws IOException {
        try (UpstreamStreamResponse ignored = stream) {
            if (stream.isEventStream()) {
                pumpEventStream(outputStream);
            } else {
                pumpRaw(outputStream);
            }
        } finally {
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    private void pumpEventStream(OutputStream out) throws IOException {
        Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), bufferSize);
        UsageAccounting usage = null;
        boolean usageAppended = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream.body(), StandardCharsets.UTF_8), bufferSize)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 首行数据到达即首 token：在本次写出前记录端到端耗时（响应头此刻尚未 commit，仍可写）
                firstTokenWriter.mark();
                String trimmed = StringUtils.trim(line);
                if (appendUsage && !usageAppended && isDoneEvent(trimmed)) {
                    usageAppended = true;
                    writeUsageEvent(writer, usage);
                } else if (appendUsage && trimmed.startsWith(DATA_PREFIX)) {
                    usage = better(usage, extractUsage(StringUtils.trim(trimmed.substring(DATA_PREFIX.length()))));
                }
                writer.write(line);
                writer.write('\n');
                writer.flush();
            }
        } finally {
            // 上游不发 [DONE]（Anthropic 以 event: message_stop 收尾）时，在流结束处兜底注入，
            // 否则调用方永远拿不到 knot_extend
            if (appendUsage && !usageAppended) {
                writeUsageEvent(writer, usage);
            }
            writer.flush();
        }
    }

    private void pumpRaw(OutputStream out) throws IOException {
        byte[] buffer = new byte[bufferSize];
        try (InputStream in = stream.body()) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                // 同 SSE 路径：首个字节就绪即首 token，写出前记录（幂等，后续 read 直接短路）
                firstTokenWriter.mark();
                out.write(buffer, 0, read);
                out.flush();
            }
        }
        out.flush();
    }

    private boolean isDoneEvent(String trimmedLine) {
        if (!trimmedLine.startsWith(DATA_PREFIX)) {
            return false;
        }
        return DONE_MARKER.equals(StringUtils.trim(trimmedLine.substring(DATA_PREFIX.length())));
    }

    /**
     * 单个 SSE 事件的用量提取。失败不影响转发，只影响最终是否注入用量事件。
     */
    private UsageAccounting extractUsage(String data) {
        if (StringUtils.isEmpty(data) || DONE_MARKER.equals(data) || !data.contains(USAGE_FIELD)) {
            return null;
        }
        return usageExtractorRegistry.extractEvent(data, stream.context(), stream.adapter());
    }

    /**
     * 写出独立的 {@code knot_extend} 事件（上游原始 usage 以并列 {@code usage} 字段透出，供下游对照排障）。
     * 用量缺失或两视图都为空时不写，避免给调用方塞一个空对象。
     */
    private void writeUsageEvent(Writer writer, UsageAccounting usage) throws IOException {
        if (usage == null) {
            return;
        }
        KnotExtendPayload payload = KnotExtendPayload.of(usage);
        if (payload == null) {
            return;
        }
        writer.write(usageEvent(payload, usage.rawUsage()));
        writer.write('\n');
    }

    private String usageEvent(KnotExtendPayload payload, Map<String, Object> rawUsage) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put(AiPayloadFields.KNOT_EXTEND, payload);
        if (rawUsage != null && !rawUsage.isEmpty()) {
            event.put(AiPayloadFields.RAW_USAGE, rawUsage);
        }
        return DATA_PREFIX + " " + JsonKit.toJson(event);
    }

    /**
     * 逐事件取 token 数更大的一份，与整包提取的 mergeMax 语义对齐。
     */
    private UsageAccounting better(UsageAccounting current, UsageAccounting candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        return candidate.totalTokens() >= current.totalTokens() ? candidate : current;
    }
}
