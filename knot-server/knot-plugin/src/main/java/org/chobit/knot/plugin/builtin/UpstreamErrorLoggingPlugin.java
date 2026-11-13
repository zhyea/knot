package org.chobit.knot.plugin.builtin;



import com.fasterxml.jackson.databind.JsonNode;

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
                context.upstreamUrl(),
                context.httpStatus(),
                errorBodyTree(context.upstreamResponseBody()));

        PluginLogWriter.log(log, context.stageCode(), binding, context.traceId(), payload);
    }

    /**
     * 上游错误体转日志树：空体（超时 / 连接失败 / 上游没给 body）落 null，不落空串——
     * 空串在日志里和「上游返回了空对象」分不开，是排障时最容易被误读的一种形态。
     */
    private static JsonNode errorBodyTree(String body) {
        return body == null || body.isBlank() ? null : JsonKit.toTree(body);
    }

}

