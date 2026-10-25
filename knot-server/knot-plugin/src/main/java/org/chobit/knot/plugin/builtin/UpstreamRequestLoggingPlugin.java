package org.chobit.knot.plugin.builtin;


import lombok.extern.slf4j.Slf4j;


import org.chobit.knot.gateway.plugin.PluginBindingView;
import org.chobit.knot.gateway.plugin.PluginDescriptor;
import org.chobit.knot.gateway.plugin.PluginExtensionPoint;
import org.chobit.knot.gateway.plugin.PluginHandler;
import org.chobit.knot.gateway.plugin.PluginStageCode;
import org.chobit.knot.plugin.log.PluginLogWriter;

import org.chobit.knot.plugin.log.UpstreamRequestPayload;

import org.chobit.knot.gateway.plugin.upstream.UpstreamPluginContext;

import org.chobit.knot.gateway.util.JsonKit;

import org.springframework.stereotype.Component;


@Slf4j

@Component

public class UpstreamRequestLoggingPlugin implements PluginHandler<UpstreamPluginContext> {


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
            context.upstreamRequestId(),
            JsonKit.toTree(context.requestBody()),
            context.upstreamUrl());

        PluginLogWriter.log(log, context.stageCode(), binding, context.traceId(), payload);
    }

}

