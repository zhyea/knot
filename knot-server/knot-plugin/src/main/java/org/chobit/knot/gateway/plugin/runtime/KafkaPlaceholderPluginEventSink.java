package org.chobit.knot.gateway.plugin.runtime;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.plugin.PluginEvent;
import org.chobit.knot.gateway.plugin.PluginEventSink;
import org.chobit.knot.gateway.plugin.config.PluginSinkProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaPlaceholderPluginEventSink implements PluginEventSink {

    private final PluginSinkProperties properties;

    public KafkaPlaceholderPluginEventSink(PluginSinkProperties properties) {
        this.properties = properties;
    }

    @Override
    public void emit(PluginEvent event) {
        if (!properties.getKafka().isEnabled()) {
            return;
        }
        log.info("plugin-event kafka placeholder topicPrefix={} bootstrapServers={} traceId={} eventType={}",
                properties.getKafka().getTopicPrefix(),
                properties.getKafka().getBootstrapServers(),
                event.traceId(),
                event.eventType());
    }
}
