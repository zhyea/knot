package org.chobit.knot.gateway.kafka;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 事件死信落盘：直推失败（同步异常 / 异步回调失败）的统一兜底。
 *
 * <p>robin 2026-10-18 拍板「数据宝贵，不能丢失」后，本类从「可选观测」升级为
 * <b>数据不丢的关键路径</b>：投递不到 Kafka 的事件必须落到本地文件，事后再回流。
 * 因此默认路径开启（{@code kafka.dlq-path} 默认 {@code logs/kafka-dlq}）。</p>
 *
 * <p>调用方有两个：请求线程（同步异常路径）与 Kafka 生产者的回调线程（异步失败路径），
 * 故 {@link #write} 必须线程安全；行格式为
 * {@code 时间 \t topic \t key \t 失败原因 \t payload}（JSONL，事后按 trace_id 回流）。</p>
 *
 * <p><b>唯一的丢失窗口</b>：死信文件本身写不进去（磁盘满 / 权限）。此时计数
 * {@code lost} 并打 ERROR 日志——已无可投之地，只能报警，不能抛回去影响请求链路。</p>
 */
@Slf4j
public class EventDeadLetterWriter {

    private static final int WARN_LOG_EVERY = 100;
    private static final int MAX_CAUSE_LENGTH = 200;

    private final Path path;
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong lost = new AtomicLong();

    /**
     * Constructs a new instance.
     *
     * @param dlqPath 死信文件路径；空白表示不落盘（失败仅计数 + 报警）
     */
    public EventDeadLetterWriter(String dlqPath) {
        String normalized = dlqPath == null || dlqPath.isBlank() ? null : dlqPath.trim();
        this.path = normalized == null ? null : Paths.get(normalized);
    }

    /**
     * 落一条死信。任何情况下不抛异常。
     */
    public synchronized void write(String topic, String key, String payload, Throwable cause) {
        if (path == null) {
            lost.incrementAndGet();
            if (lost.get() % WARN_LOG_EVERY == 1) {
                log.warn("Kafka dead letter path is not configured, message lost (total={}); "
                        + "set kafka.dlq-path to persist overflow", lost.get());
            }
            return;
        }
        try {
            Files.createDirectories(path.getParent() == null ? Paths.get(".") : path.getParent());
            String line = LocalDateTime.now() + "\t" + topic + "\t" + key + "\t"
                    + causeOf(cause) + "\t" + payload + System.lineSeparator();
            Files.writeString(path, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            written.incrementAndGet();
        } catch (IOException | RuntimeException e) {
            lost.incrementAndGet();
            if (lost.get() % WARN_LOG_EVERY == 1) {
                log.error("Failed to write kafka dead letter to {} (total lost={})", path, lost.get(), e);
            }
        }
    }

    /**
     * 成功落盘的死信数。
     */
    public long written() {
        return written.get();
    }

    /**
     * 未能落盘（含未配置路径）而死信计数——真正的丢失，只能靠 ERROR/WARN 日志报警。
     */
    public long lost() {
        return lost.get();
    }

    private static String causeOf(Throwable cause) {
        if (cause == null) {
            return "unknown";
        }
        String message = cause.getMessage();
        String text = message == null || message.isBlank()
                ? cause.getClass().getSimpleName()
                : cause.getClass().getSimpleName() + ": " + message.trim();
        return text.length() <= MAX_CAUSE_LENGTH ? text : text.substring(0, MAX_CAUSE_LENGTH);
    }
}
