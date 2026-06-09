package org.chobit.knot.gateway.plugin;

import java.time.LocalDateTime;
import java.util.Map;

public record PluginEvent(String eventType,
                          String traceId,
                          String traceparent,
                          String pluginInstanceCode,
                          String pluginCapabilityCode,
                          PluginStageCode stageCode,
                          String scopeType,
                          Long scopeRefId,
                          Map<String, Object> payload,
                          LocalDateTime occurredAt) {
}
