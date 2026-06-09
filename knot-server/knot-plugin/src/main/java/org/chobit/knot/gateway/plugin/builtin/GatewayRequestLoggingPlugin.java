package org.chobit.knot.gateway.plugin.builtin;

import org.chobit.knot.gateway.plugin.*;
import org.chobit.knot.gateway.plugin.gateway.GatewayPluginContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GatewayRequestLoggingPlugin implements PluginHandler<GatewayPluginContext> {

    private final PluginEventSink eventSink;

    public GatewayRequestLoggingPlugin(PluginEventSink eventSink) {
        this.eventSink = eventSink;
    }

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "gateway-request-response-log",
                PluginExtensionPoint.GATEWAY_EXCHANGE,
                PluginStageCode.GATEWAY_REQUEST
        );
    }

    @Override
    public Class<GatewayPluginContext> contextType() {
        return GatewayPluginContext.class;
    }

    @Override
    public void handle(PluginBindingView binding, GatewayPluginContext context) {
        eventSink.emit(buildEvent(binding, context, "gateway-request"));
    }

    private PluginEvent buildEvent(PluginBindingView binding, GatewayPluginContext context, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleCode", context.ruleCode());
        payload.put("protocol", context.protocol());
        payload.put("contentType", context.contentType() == null ? null : context.contentType().toString());
        payload.put("requestBody", context.requestBody());
        payload.put("routing", context.routingSnapshot());
        return new PluginEvent(
                eventType,
                context.traceId(),
                context.traceparent(),
                binding.instanceCode(),
                binding.capabilityCode(),
                context.stageCode(),
                binding.scopeType() == null ? null : binding.scopeType().name(),
                binding.scopeRefId(),
                payload,
                LocalDateTime.now()
        );
    }
}
