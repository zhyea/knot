package org.chobit.knot.gateway.plugin.builtin;

import lombok.extern.slf4j.Slf4j;

import org.chobit.knot.gateway.plugin.PluginBindingView;
import org.chobit.knot.gateway.plugin.PluginDescriptor;
import org.chobit.knot.gateway.plugin.PluginExtensionPoint;
import org.chobit.knot.gateway.plugin.PluginHandler;
import org.chobit.knot.gateway.plugin.PluginStageCode;
import org.chobit.knot.gateway.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.gateway.plugin.log.GatewayRequestPayload;
import org.chobit.knot.gateway.plugin.log.PluginLogWriter;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GatewayRequestLoggingPlugin implements PluginHandler<GatewayPluginContext> {

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
        PluginLogWriter.log(log, context.stageCode(), binding, context.traceId(), payload);
    }
}
