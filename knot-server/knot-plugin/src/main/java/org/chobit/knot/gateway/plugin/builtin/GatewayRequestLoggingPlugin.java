package org.chobit.knot.gateway.plugin.builtin;

import org.chobit.knot.gateway.plugin.*;
import org.chobit.knot.gateway.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.gateway.plugin.log.GatewayRequestPayload;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

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
        eventSink.emit(buildEvent(binding, context, PluginEventType.GATEWAY_REQUEST));
    }

    private PluginEvent buildEvent(PluginBindingView binding, GatewayPluginContext context, PluginEventType eventType) {
        GatewayRequestPayload payload = new GatewayRequestPayload(
                context.ruleCode(),
                context.protocol(),
                context.contentType() == null ? null : context.contentType().toString(),
                JsonKit.toTree(context.requestBody()),
                context.routingSnapshot());
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
