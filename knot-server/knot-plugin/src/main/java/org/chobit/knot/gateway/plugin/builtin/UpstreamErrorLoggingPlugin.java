package org.chobit.knot.gateway.plugin.builtin;

import org.chobit.knot.gateway.plugin.*;
import org.chobit.knot.gateway.plugin.log.UpstreamErrorPayload;
import org.chobit.knot.gateway.plugin.upstream.UpstreamPluginContext;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UpstreamErrorLoggingPlugin implements PluginHandler<UpstreamPluginContext> {

    private final PluginEventSink eventSink;

    public UpstreamErrorLoggingPlugin(PluginEventSink eventSink) {
        this.eventSink = eventSink;
    }

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "provider-request-response-log",
                PluginExtensionPoint.UPSTREAM_EXCHANGE,
                PluginStageCode.UPSTREAM_ERROR
        );
    }

    @Override
    public Class<UpstreamPluginContext> contextType() {
        return UpstreamPluginContext.class;
    }

    @Override
    public void handle(PluginBindingView binding, UpstreamPluginContext context) {
        Throwable error = context.error();
        UpstreamErrorPayload payload = new UpstreamErrorPayload(
                context.providerCode(),
                context.modelCode(),
                context.protocol(),
                error == null ? null : error.getClass().getSimpleName(),
                error == null ? null : error.getMessage(),
                JsonKit.toTree(context.requestBody()));
        eventSink.emit(new PluginEvent(
                PluginEventType.PROVIDER_ERROR,
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
