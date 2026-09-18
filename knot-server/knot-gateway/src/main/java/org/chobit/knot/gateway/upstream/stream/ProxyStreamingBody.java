package org.chobit.knot.gateway.upstream.stream;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.model.NormalizedUsage;
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
 * <p>SSE 场景额外做一件事：逐事件累计用量，并在上游 {@code data: [DONE]} 之前插入
 * {@code knot_usage} 事件，与整包缓冲模式下的行为保持一致。</p>
 */
public class ProxyStreamingBody implements StreamingResponseBody {

    private static final String DATA_PREFIX = "data:";
    private static final String DONE_MARKER = "[DONE]";
    private static final String USAGE_FIELD = "usage";

    private final UpstreamStreamResponse stream;
    private final UsageExtractorRegistry usageExtractorRegistry;
    private final boolean appendUsage;
    private final int bufferSize;

    public ProxyStreamingBody(UpstreamStreamResponse stream,
                              UsageExtractorRegistry usageExtractorRegistry,
                              boolean appendUsage,
                              int bufferSize) {
        this.stream = stream;
        this.usageExtractorRegistry = usageExtractorRegistry;
        this.appendUsage = appendUsage;
        this.bufferSize = bufferSize > 0 ? bufferSize : 8192;
    }

    @Override
    public void writeTo(OutputStream outputStream) throws IOException {
        try (UpstreamStreamResponse ignored = stream) {
            if (stream.isEventStream()) {
                pumpEventStream(outputStream);
            } else {
                pumpRaw(outputStream);
            }
        }
    }

    private void pumpEventStream(OutputStream out) throws IOException {
        Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), bufferSize);
        NormalizedUsage usage = null;
        boolean usageAppended = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream.body(), StandardCharsets.UTF_8), bufferSize)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = StringUtils.trim(line);
                if (appendUsage) {
                    if (!usageAppended && isDoneEvent(trimmed)) {
                        usageAppended = true;
                        if (usage != null) {
                            writer.write(usageEvent(usage));
                            writer.write('\n');
                        }
                    } else if (trimmed.startsWith(DATA_PREFIX)) {
                        usage = better(usage, extractUsage(StringUtils.trim(trimmed.substring(DATA_PREFIX.length()))));
                    }
                }
                writer.write(line);
                writer.write('\n');
                writer.flush();
            }
        } finally {
            writer.flush();
        }
    }

    private void pumpRaw(OutputStream out) throws IOException {
        byte[] buffer = new byte[bufferSize];
        try (InputStream in = stream.body()) {
            int read;
            while ((read = in.read(buffer)) != -1) {
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
    private NormalizedUsage extractUsage(String data) {
        if (StringUtils.isEmpty(data) || DONE_MARKER.equals(data) || !data.contains(USAGE_FIELD)) {
            return null;
        }
        return usageExtractorRegistry.extractEvent(data, stream.context(), stream.adapter());
    }

    private String usageEvent(NormalizedUsage usage) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put(AiPayloadFields.KNOT_USAGE, usage);
        return DATA_PREFIX + " " + JsonKit.toJson(event);
    }

    /**
     * 逐事件取 token 数更大的一份，与整包提取的 mergeMax 语义对齐。
     */
    private NormalizedUsage better(NormalizedUsage current, NormalizedUsage candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        long currentTokens = current.totalTokens() == null ? 0L : current.totalTokens();
        long candidateTokens = candidate.totalTokens() == null ? 0L : candidate.totalTokens();
        return candidateTokens >= currentTokens ? candidate : current;
    }
}
