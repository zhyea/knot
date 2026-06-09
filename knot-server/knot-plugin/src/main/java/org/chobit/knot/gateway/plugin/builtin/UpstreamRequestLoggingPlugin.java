package org.chobit.knot.gateway.plugin.builtin;

import org.chobit.knot.gateway.plugin.*;
import org.chobit.knot.gateway.plugin.upstream.UpstreamPluginContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("providerCode", context.providerCode());
        payload.put("modelCode", context.modelCode());
        payload.put("protocol", context.protocol());
        payload.put("bindingProtocol", context.bindingProtocol());
        payload.put("contentType", context.contentType() == null ? null : context.contentType().toString());
        payload.put("requestBody", context.requestBody());
        eventSink.emit(new PluginEvent(
                "provider-request",
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
