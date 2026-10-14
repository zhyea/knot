package org.chobit.knot.gateway.plugin.builtin;

import org.chobit.knot.gateway.plugin.*;
import org.chobit.knot.gateway.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.gateway.plugin.log.GatewayErrorPayload;
import org.chobit.knot.gateway.plugin.log.PluginLogWriter;
import org.chobit.knot.gateway.util.JsonKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GatewayErrorLoggingPlugin implements PluginHandler<GatewayPluginContext> {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorLoggingPlugin.class);

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
        PluginLogWriter.log(log, context.stageCode(), binding, context.traceId(), payload);
    }
}
