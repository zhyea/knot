package org.chobit.knot.plugin.builtin;



import lombok.extern.slf4j.Slf4j;



import org.chobit.knot.plugin.PluginBindingView;
import org.chobit.knot.plugin.PluginDescriptor;
import org.chobit.knot.plugin.PluginExtensionPoint;
import org.chobit.knot.plugin.PluginHandler;
import org.chobit.knot.plugin.PluginStageCode;
import org.chobit.knot.plugin.log.PluginLogWriter;

import org.chobit.knot.plugin.log.UpstreamResponsePayload;

import org.chobit.knot.plugin.upstream.UpstreamPluginContext;

import org.chobit.knot.common.util.JsonKit;

import org.springframework.stereotype.Component;



@Slf4j

@Component

public class UpstreamResponseLoggingPlugin implements PluginHandler<UpstreamPluginContext> {



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
                context.upstreamRequestId(),
                JsonKit.toTree(context.responseSnapshot()),
                context.upstreamUrl());

        PluginLogWriter.log(log, context.stageCode(), binding, context.traceId(), payload);
    }

}

