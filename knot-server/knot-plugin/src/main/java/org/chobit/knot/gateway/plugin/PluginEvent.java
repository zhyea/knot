package org.chobit.knot.gateway.plugin;

import org.chobit.knot.gateway.plugin.log.PluginLogPayload;

import java.time.LocalDateTime;

public record PluginEvent(PluginEventType eventType,
                          String traceId,
                          String traceparent,
                          String pluginInstanceCode,
                          String pluginCapabilityCode,
                          PluginStageCode stageCode,
                          String scopeType,
                          Long scopeRefId,
                          PluginLogPayload payload,
                          LocalDateTime occurredAt) {
}
