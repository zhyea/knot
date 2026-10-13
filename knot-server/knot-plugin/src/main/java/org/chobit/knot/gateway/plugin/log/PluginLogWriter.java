package org.chobit.knot.gateway.plugin.log;

import org.chobit.knot.gateway.plugin.PluginBindingView;
import org.chobit.knot.gateway.plugin.PluginStageCode;
import org.chobit.knot.gateway.util.JsonKit;
import org.slf4j.Logger;

/**
 * 同步日志插件的统一输出出口。
 *
 * <p>原先同步日志插件把活儿转交给 {@code PluginEventSink} 异步链路（{@code LoggingPluginEventSink} /
 * {@code KafkaPlaceholderPluginEventSink}）完成日志输出，与「同步插件应在请求链路内联完成」的预期不符。
 * 这里改为直接经 Slf4j 输出结构化日志，字段格式与旧 {@code LoggingPluginEventSink} 保持一致，
 * 仅日志分类（logger category）改为各自 handler 类，便于按插件维度过滤。
 */
public final class PluginLogWriter {

    private PluginLogWriter() {
    }

    public static void log(Logger log,
                          String eventTypeCode,
                          PluginStageCode stageCode,
                          PluginBindingView binding,
                          String traceId,
                          PluginLogPayload payload) {
        log.info("plugin-event type={} stage={} instance={} capability={} traceId={} payload={}",
                eventTypeCode,
                stageCode == null ? null : stageCode.code(),
                binding.instanceCode(),
                binding.capabilityCode(),
                traceId,
                JsonKit.toJson(payload));
    }
}
