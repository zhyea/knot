package org.chobit.knot.gateway.plugin.builtin;

import org.chobit.knot.gateway.plugin.*;
import org.chobit.knot.gateway.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.gateway.plugin.log.GatewayRequestPayload;
import org.chobit.knot.gateway.plugin.log.PluginLogWriter;
import org.chobit.knot.gateway.util.JsonKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GatewayRequestLoggingPlugin implements PluginHandler<GatewayPluginContext> {

    private static final Logger log = LoggerFactory.getLogger(GatewayRequestLoggingPlugin.class);

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "gateway-request-response-log",
                PluginExtensionPoint.GATEWAY_EXCHANGE,
                PluginStageCode.GATEWAY_REQUEST
        );
    }

    @Override
    public Class<GatewayPluginContext> contextType() {
        return GatewayPluginContext.class;
    }

    @Override
    public void handle(PluginBindingView binding, GatewayPluginContext context) {
        GatewayRequestPayload payload = new GatewayRequestPayload(
                context.ruleCode(),
                context.protocol(),
                context.contentType() == null ? null : context.contentType().toString(),
                JsonKit.toTree(context.requestBody()),
                context.routingSnapshot());
        PluginLogWriter.log(log, PluginEventType.GATEWAY_REQUEST.code(), context.stageCode(), binding, context.traceId(), payload);
    }
}
