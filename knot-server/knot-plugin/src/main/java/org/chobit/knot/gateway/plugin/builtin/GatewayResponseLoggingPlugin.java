package org.chobit.knot.gateway.plugin.builtin;

import org.chobit.knot.gateway.plugin.*;
import org.chobit.knot.gateway.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.gateway.plugin.log.GatewayResponsePayload;
import org.chobit.knot.gateway.plugin.log.PluginLogWriter;
import org.chobit.knot.gateway.util.JsonKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GatewayResponseLoggingPlugin implements PluginHandler<GatewayPluginContext> {

    private static final Logger log = LoggerFactory.getLogger(GatewayResponseLoggingPlugin.class);

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "gateway-request-response-log",
                PluginExtensionPoint.GATEWAY_EXCHANGE,
                PluginStageCode.GATEWAY_RESPONSE
        );
    }

    @Override
    public Class<GatewayPluginContext> contextType() {
        return GatewayPluginContext.class;
    }

    @Override
    public void handle(PluginBindingView binding, GatewayPluginContext context) {
        GatewayResponsePayload payload = new GatewayResponsePayload(
                context.ruleCode(),
                context.protocol(),
                JsonKit.toTree(context.responseBody()));
        PluginLogWriter.log(log, context.stageCode(), binding, context.traceId(), payload);
    }
}
