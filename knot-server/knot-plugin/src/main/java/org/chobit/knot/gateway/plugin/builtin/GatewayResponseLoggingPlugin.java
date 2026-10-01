package org.chobit.knot.gateway.plugin.builtin;

import org.chobit.knot.gateway.plugin.*;
import org.chobit.knot.gateway.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.gateway.plugin.log.GatewayResponsePayload;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class GatewayResponseLoggingPlugin implements PluginHandler<GatewayPluginContext> {

    private final PluginEventSink eventSink;

    public GatewayResponseLoggingPlugin(PluginEventSink eventSink) {
        this.eventSink = eventSink;
    }

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "gateway-request-response-log",
                PluginExtensionPoint.GATEWAY_EXCHANGE,
                PluginStageCode.GATEWAY_RESPONSE
        );
    }

    @Override
    public Class<GatewayPluginContext> contextType() {
        return GatewayPluginContext.class;
    }

    @Override
    public void handle(PluginBindingView binding, GatewayPluginContext context) {
        GatewayResponsePayload payload = new GatewayResponsePayload(
                context.ruleCode(),
                context.protocol(),
                JsonKit.toTree(context.responseBody()));
        eventSink.emit(new PluginEvent(
                PluginEventType.GATEWAY_RESPONSE,
                context.traceId(),
                context.traceparent(),
                binding.instanceCode(),
                binding.capabilityCode(),
                context.stageCode(),
                binding.scopeType() == null ? null : binding.scopeType().name(),
                binding.scopeRefId(),
                payload,
                LocalDateTime.now()
        ));
    }
}
