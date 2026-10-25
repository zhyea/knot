package org.chobit.knot.plugin.log;

import org.chobit.knot.gateway.plugin.PluginBindingView;
import org.chobit.knot.gateway.plugin.PluginStageCode;
import org.chobit.knot.gateway.util.JsonKit;
import org.slf4j.Logger;

/**
 * 同步日志插件的统一输出出口。
 *
 * <p>同步日志插件经 Slf4j 直接输出结构化日志，字段格式一致，仅日志分类（logger category）改为各自 handler 类，
 * 便于按插件维度过滤。
 */
public final class PluginLogWriter {

    private PluginLogWriter() {
    }

    public static void log(Logger log,
                          PluginStageCode stageCode,
                          PluginBindingView binding,
                          String traceId,
                          PluginLogPayload payload) {
        log.info("plugin-event stage={} instance={} capability={} traceId={} payload={}",
                stageCode == null ? null : stageCode.code(),
                binding.instanceCode(),
                binding.capabilityCode(),
                traceId,
                JsonKit.toJson(payload));
    }
}
