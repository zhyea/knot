package org.chobit.knot.gateway.plugin.builtin;

import org.chobit.knot.gateway.plugin.*;
import org.chobit.knot.gateway.plugin.log.PluginLogWriter;
import org.chobit.knot.gateway.plugin.log.UpstreamRequestPayload;
import org.chobit.knot.gateway.plugin.upstream.UpstreamPluginContext;
import org.chobit.knot.gateway.util.JsonKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UpstreamRequestLoggingPlugin implements PluginHandler<UpstreamPluginContext> {

    private static final Logger log = LoggerFactory.getLogger(UpstreamRequestLoggingPlugin.class);

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
        PluginLogWriter.log(log, PluginEventType.PROVIDER_REQUEST.code(), context.stageCode(), binding, context.traceId(), payload);
    }
}
