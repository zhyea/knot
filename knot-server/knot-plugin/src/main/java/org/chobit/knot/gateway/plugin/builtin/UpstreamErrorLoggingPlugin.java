package org.chobit.knot.gateway.plugin.builtin;

import lombok.extern.slf4j.Slf4j;

import org.chobit.knot.gateway.plugin.*;
import org.chobit.knot.gateway.plugin.log.PluginLogWriter;
import org.chobit.knot.gateway.plugin.log.UpstreamErrorPayload;
import org.chobit.knot.gateway.plugin.upstream.UpstreamPluginContext;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UpstreamErrorLoggingPlugin implements PluginHandler<UpstreamPluginContext> {

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
        PluginLogWriter.log(log, context.stageCode(), binding, context.traceId(), payload);
    }
}
