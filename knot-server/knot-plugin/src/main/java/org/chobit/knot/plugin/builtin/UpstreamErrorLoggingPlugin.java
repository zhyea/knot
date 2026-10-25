package org.chobit.knot.plugin.builtin;



import lombok.extern.slf4j.Slf4j;



import org.chobit.knot.plugin.PluginBindingView;
import org.chobit.knot.plugin.PluginDescriptor;
import org.chobit.knot.plugin.PluginExtensionPoint;
import org.chobit.knot.plugin.PluginHandler;
import org.chobit.knot.plugin.PluginStageCode;
import org.chobit.knot.plugin.log.PluginLogWriter;

import org.chobit.knot.plugin.log.UpstreamErrorPayload;

import org.chobit.knot.plugin.upstream.UpstreamPluginContext;

import org.chobit.knot.common.util.JsonKit;

import org.springframework.stereotype.Component;



@Slf4j

@Component

public class UpstreamErrorLoggingPlugin implements PluginHandler<UpstreamPluginContext> {



    @Override

    public PluginDescriptor descriptor() {

        return new PluginDescriptor(
                "provider-error-log",
                "上游异常日志插件",
                "provider-request-response-log",
                PluginExtensionPoint.UPSTREAM_EXCHANGE,
                PluginStageCode.UPSTREAM_ERROR,
                100
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
                context.upstreamRequestId(),
                error == null ? null : error.getClass().getSimpleName(),
                error == null ? null : error.getMessage(),
                JsonKit.toTree(context.requestBody()),
                context.upstreamUrl());

        PluginLogWriter.log(log, context.stageCode(), binding, context.traceId(), payload);
    }

}

