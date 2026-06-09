package org.chobit.knot.gateway.plugin.runtime;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.plugin.PluginEvent;
import org.chobit.knot.gateway.plugin.PluginEventSink;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingPluginEventSink implements PluginEventSink {

    @Override
    public void emit(PluginEvent event) {
        log.info("plugin-event type={} stage={} instance={} capability={} traceId={} scope={} scopeRefId={} payload={}",
                event.eventType(),
                event.stageCode() == null ? null : event.stageCode().code(),
                event.pluginInstanceCode(),
                event.pluginCapabilityCode(),
                event.traceId(),
                event.scopeType(),
                event.scopeRefId(),
                JsonKit.toJson(event.payload()));
    }
}
