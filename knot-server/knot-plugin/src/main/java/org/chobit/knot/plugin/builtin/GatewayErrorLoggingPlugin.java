package org.chobit.knot.plugin.builtin;

import lombok.extern.slf4j.Slf4j;

import org.chobit.knot.plugin.PluginBindingView;
import org.chobit.knot.plugin.PluginDescriptor;
import org.chobit.knot.plugin.PluginExtensionPoint;
import org.chobit.knot.plugin.PluginHandler;
import org.chobit.knot.plugin.PluginStageCode;
import org.chobit.knot.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.plugin.log.GatewayErrorPayload;
import org.chobit.knot.plugin.log.PluginLogWriter;
import org.chobit.knot.common.util.JsonKit;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GatewayErrorLoggingPlugin implements PluginHandler<GatewayPluginContext> {

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
