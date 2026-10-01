package org.chobit.knot.gateway.plugin.builtin;

import org.chobit.knot.gateway.plugin.*;
import org.chobit.knot.gateway.plugin.log.UpstreamRequestPayload;
import org.chobit.knot.gateway.plugin.upstream.UpstreamPluginContext;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UpstreamRequestLoggingPlugin implements PluginHandler<UpstreamPluginContext> {

    private final PluginEventSink eventSink;

    public UpstreamRequestLoggingPlugin(PluginEventSink eventSink) {
        this.eventSink = eventSink;
    }

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "provider-request-response-log",
                PluginExtensionPoint.UPSTREAM_EXCHANGE,
                PluginStageCode.UPSTREAM_REQUEST
        );
    }

    @Override
    public Class<UpstreamPluginContext> contextType() {
        return UpstreamPluginContext.class;
    }

    @Override
    public void handle(PluginBindingView binding, UpstreamPluginContext context) {
        UpstreamRequestPayload payload = new UpstreamRequestPayload(
                context.providerCode(),
                context.modelCode(),
                context.protocol(),
                context.bindingProtocol(),
                context.contentType() == null ? null : context.contentType().toString(),
                JsonKit.toTree(context.requestBody()));
        eventSink.emit(new PluginEvent(
                PluginEventType.PROVIDER_REQUEST,
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
