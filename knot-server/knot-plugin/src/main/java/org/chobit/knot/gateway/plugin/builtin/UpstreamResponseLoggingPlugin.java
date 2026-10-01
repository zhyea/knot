package org.chobit.knot.gateway.plugin.builtin;

import org.chobit.knot.gateway.plugin.*;
import org.chobit.knot.gateway.plugin.log.UpstreamResponsePayload;
import org.chobit.knot.gateway.plugin.upstream.UpstreamPluginContext;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UpstreamResponseLoggingPlugin implements PluginHandler<UpstreamPluginContext> {

    private final PluginEventSink eventSink;

    public UpstreamResponseLoggingPlugin(PluginEventSink eventSink) {
        this.eventSink = eventSink;
    }

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "provider-request-response-log",
                PluginExtensionPoint.UPSTREAM_EXCHANGE,
                PluginStageCode.UPSTREAM_RESPONSE
        );
    }

    @Override
    public Class<UpstreamPluginContext> contextType() {
        return UpstreamPluginContext.class;
    }

    @Override
    public void handle(PluginBindingView binding, UpstreamPluginContext context) {
        UpstreamResponsePayload payload = new UpstreamResponsePayload(
                context.providerCode(),
                context.modelCode(),
                context.protocol(),
                JsonKit.toTree(context.responseSnapshot()));
        eventSink.emit(new PluginEvent(
                PluginEventType.PROVIDER_RESPONSE,
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
