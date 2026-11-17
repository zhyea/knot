package org.chobit.knot.admin.service.support;

import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.svc.dto.routing.RoutingTestPreparation;
import org.chobit.knot.svc.dto.routing.RoutingTestStreamHandle;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * 把网关响应翻译成管理端 SSE 事件流：{@code meta} → 若干 {@code chunk} → {@code complete}，
 * 上游 4xx/5xx 与写入异常走 {@code error}。
 *
 * <p>事件字段名是前端契约（{@code RoutingRuleTestDrawer} 按名字取值），改名等于改接口。
 * {@code meta} 必须先于首个 chunk：那时网关响应头已到手，命中模型、协议与 curl 才确定。</p>
 *
 * <p>写事件后立即 flush，保证浏览器端增量可见；句柄由调用方以 {@code try-with-resources} 关闭，
 * 覆盖正常结束、上游异常与浏览器取消三条路径。</p>
 */
public final class RoutingTestStreamWriter {

    /** 流式读取缓冲区大小（字节） */
    private static final int BUFFER_SIZE = 8 * 1024;

    private RoutingTestStreamWriter() {
    }

    /** 首个事件：本次测试命中了什么、按什么协议发的、curl 长什么样 */
    public record MetaEvent(Long matchedRuleId,
                            String targetProviderAccountCode,
                            Long targetModelId,
                            String modelCode,
                            String protocol,
                            String curl) {
    }

    /** 失败事件：{@code httpStatus} 为 0 表示上游状态未知（连接已异常） */
    public record ErrorEvent(String status, int httpStatus, String errorMessage, String responseBody) {
    }

    /** 结束事件：{@code bytes} 是透传的原始字节数，{@code durationMs} 从开始写流算起 */
    public record CompleteEvent(String status, int httpStatus, long bytes, long durationMs) {
    }

    /** 文本片段：原样透传上游字节，不做行切分 */
    public record TextChunkEvent(String text) {
    }

    /** 二进制片段：按 base64 传输，{@code contentType} 为空串表示上游未给 */
    public record BinaryChunkEvent(String encoding, String content, String contentType) {
    }

    /**
     * 逐块读取并写出事件流。
     *
     * @param out    响应输出流
     * @param handle 已打开的网关测试句柄，本方法负责关闭
     */
    public static void write(OutputStream out, RoutingTestStreamHandle handle) {
        long startedAt = System.currentTimeMillis();
        try (handle) {
            RoutingTestPreparation prep = handle.preparation();
            writeEvent(out, "meta", new MetaEvent(
                    prep.rule().id(),
                    nullSafe(prep.target().providerAccountCode()),
                    prep.target().targetId(),
                    nullSafe(prep.model()),
                    prep.protocol().code(),
                    nullSafe(prep.curl())));

            HttpStatusCode upstreamStatus = handle.statusCode();
            if (upstreamStatus.isError()) {
                // 网关在首字节前就返回了 4xx/5xx：SSE 外层已是 200，真实状态由事件内的 httpStatus 承载
                writeEvent(out, "error", new ErrorEvent(
                        "FAILED",
                        upstreamStatus.value(),
                        "网关返回 HTTP " + upstreamStatus.value(),
                        readAllText(handle.bodyStream())));
                return;
            }

            MediaType contentType = handle.contentType();
            boolean textual = isTextual(contentType);
            byte[] buffer = new byte[BUFFER_SIZE];
            long bytes = 0;
            try (InputStream in = handle.bodyStream()) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    bytes += read;
                    writeEvent(out, "chunk", textual
                            ? new TextChunkEvent(new String(buffer, 0, read, StandardCharsets.UTF_8))
                            : new BinaryChunkEvent("base64",
                            Base64.getEncoder().encodeToString(Arrays.copyOfRange(buffer, 0, read)),
                            contentType == null ? "" : contentType.toString()));
                }
            }
            writeEvent(out, "complete", new CompleteEvent(
                    "SUCCESS",
                    upstreamStatus.value(),
                    bytes,
                    System.currentTimeMillis() - startedAt));
        } catch (IOException | RuntimeException ex) {
            // 浏览器取消时写不出去属正常现象：此时只能保证资源已被 try-with-resources 释放
            writeErrorQuietly(out, ex);
        }
    }

    /** 写单个事件：event 行 + data 行 + 空行分隔，随后 flush 保证增量可见 */
    private static void writeEvent(OutputStream out, String event, Object data) throws IOException {
        out.write(("event: " + event + "\n").getBytes(StandardCharsets.UTF_8));
        out.write(("data: " + JsonKit.toJsonOrThrow(data) + "\n").getBytes(StandardCharsets.UTF_8));
        out.write('\n');
        out.flush();
    }

    private static void writeErrorQuietly(OutputStream out, Exception ex) {
        try {
            writeEvent(out, "error", new ErrorEvent(
                    "FAILED",
                    0,
                    String.valueOf(ex.getMessage()),
                    ""));
        } catch (IOException | RuntimeException ignored) {
            // 连接已断开（用户主动停止 / 上游异常），无法再向客户端补发错误事件
        }
    }

    private static String readAllText(InputStream in) throws IOException {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 文本类响应按原始顺序透传；二进制（如音频）走 base64 通道。
     * 无 {@code contentType} 时按文本处理——上游漏给头时文本透传比 base64 更有用。
     */
    private static boolean isTextual(MediaType contentType) {
        if (contentType == null) {
            return true;
        }
        String type = contentType.getType();
        if ("text".equalsIgnoreCase(type)) {
            return true;
        }
        if (!"application".equalsIgnoreCase(type)) {
            return false;
        }
        String subtype = contentType.getSubtype();
        return subtype == null
                || subtype.endsWith("json")
                || subtype.endsWith("+json")
                || "x-ndjson".equalsIgnoreCase(subtype)
                || "xml".equalsIgnoreCase(subtype);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
