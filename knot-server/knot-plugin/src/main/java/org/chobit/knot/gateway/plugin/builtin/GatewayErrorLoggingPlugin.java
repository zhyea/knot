package org.chobit.knot.gateway.plugin.builtin;

import org.chobit.knot.gateway.plugin.*;
import org.chobit.knot.gateway.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.gateway.plugin.log.GatewayErrorPayload;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class GatewayErrorLoggingPlugin implements PluginHandler<GatewayPluginContext> {

    private final PluginEventSink eventSink;

    public GatewayErrorLoggingPlugin(PluginEventSink eventSink) {
        this.eventSink = eventSink;
    }

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "gateway-request-response-log",
                PluginExtensionPoint.GATEWAY_EXCHANGE,
                PluginStageCode.GATEWAY_ERROR
        );
    }

    @Override
    public Class<GatewayPluginContext> contextType() {
        return GatewayPluginContext.class;
    }

    @Override
    public void handle(PluginBindingView binding, GatewayPluginContext context) {
        Throwable error = context.error();
        GatewayErrorPayload payload = new GatewayErrorPayload(
                context.ruleCode(),
                context.protocol(),
                error == null ? null : error.getClass().getSimpleName(),
                error == null ? null : error.getMessage(),
                JsonKit.toTree(context.requestBody()));
        eventSink.emit(new PluginEvent(
                PluginEventType.GATEWAY_ERROR,
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
