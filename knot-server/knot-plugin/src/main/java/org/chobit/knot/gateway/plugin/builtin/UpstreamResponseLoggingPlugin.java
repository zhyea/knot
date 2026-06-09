package org.chobit.knot.gateway.plugin.builtin;

import org.chobit.knot.gateway.plugin.*;
import org.chobit.knot.gateway.plugin.upstream.UpstreamPluginContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("providerCode", context.providerCode());
        payload.put("modelCode", context.modelCode());
        payload.put("protocol", context.protocol());
        payload.put("response", context.responseSnapshot());
        eventSink.emit(new PluginEvent(
                "provider-response",
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
