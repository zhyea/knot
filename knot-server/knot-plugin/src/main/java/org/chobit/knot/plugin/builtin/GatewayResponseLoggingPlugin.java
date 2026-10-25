package org.chobit.knot.plugin.builtin;

import lombok.extern.slf4j.Slf4j;

import org.chobit.knot.plugin.PluginBindingView;
import org.chobit.knot.plugin.PluginDescriptor;
import org.chobit.knot.plugin.PluginExtensionPoint;
import org.chobit.knot.plugin.PluginHandler;
import org.chobit.knot.plugin.PluginStageCode;
import org.chobit.knot.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.plugin.log.GatewayResponsePayload;
import org.chobit.knot.plugin.log.PluginLogWriter;
import org.chobit.knot.common.util.JsonKit;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GatewayResponseLoggingPlugin implements PluginHandler<GatewayPluginContext> {

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
