package org.chobit.knot.gateway.plugin.builtin;

import org.chobit.knot.gateway.plugin.*;
import org.chobit.knot.gateway.plugin.log.PluginLogWriter;
import org.chobit.knot.gateway.plugin.log.UpstreamResponsePayload;
import org.chobit.knot.gateway.plugin.upstream.UpstreamPluginContext;
import org.chobit.knot.gateway.util.JsonKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UpstreamResponseLoggingPlugin implements PluginHandler<UpstreamPluginContext> {

    private static final Logger log = LoggerFactory.getLogger(UpstreamResponseLoggingPlugin.class);

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
        PluginLogWriter.log(log, PluginEventType.PROVIDER_RESPONSE.code(), context.stageCode(), binding, context.traceId(), payload);
    }
}
