package org.chobit.knot.gateway.runtime;

import org.chobit.knot.gateway.constants.AuthConstants;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import org.chobit.knot.gateway.service.ModerationWordResource;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.plugin.PluginDispatcher;
import org.chobit.knot.gateway.plugin.PluginExtensionPoint;
import org.chobit.knot.gateway.plugin.PluginStageCode;
import org.chobit.knot.gateway.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.gateway.plugin.gateway.GatewayRoutingSnapshot;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.vo.request.GatewayModelRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;
import java.util.function.LongConsumer;

/**
 * Handles the incoming request flow. Executes the public operation.
 */
public abstract class AbstractGatewayRequestTemplate {

    private final PluginDispatcher pluginDispatcher;

    /**
     * 词库资源供给（网关唯一的审核职责）：每请求取全量启用词条随插件上下文下发，
     * 列表由 {@link ModerationWordResource} 按路由数据同款节奏缓存（约 30s 生效延迟），
     * 编译 / 去重 / 定时重建已收敛到 knot-plugin 的 {@code ModerationWordCache}。
     */
    private final ModerationWordResource moderationWordResource;

    protected AbstractGatewayRequestTemplate(PluginDispatcher pluginDispatcher,
                                             ModerationWordResource moderationWordResource) {
        this.pluginDispatcher = pluginDispatcher;
        this.moderationWordResource = moderationWordResource;
    }

    /**
     * Gateway request template entrypoint:
     * validate required headers,
     * resolve routing,
     * proxy the adapted request upstream, then apply post-response usage accounting.
     */
    public final Object handle(String authorization,
                               String ruleCode,
                               String traceparent,
                               String stickyKey,
                               GatewayModelRequest request,
                               ModelApiProtocolEnum protocol) {
        return handleMap(authorization, ruleCode, traceparent, stickyKey,
                JsonKit.toMap(request), protocol, MediaType.APPLICATION_JSON);
    }

    /**
     * Gateway request template entrypoint for multipart requests.
     */
    public final Object handleMultipart(String authorization,
                                        String ruleCode,
                                        String traceparent,
                                        String stickyKey,
                                        Map<String, Object> requestBody,
                                        ModelApiProtocolEnum protocol) {
        return handleMap(authorization, ruleCode, traceparent, stickyKey,
                requestBody, protocol, MediaType.MULTIPART_FORM_DATA);
    }

    private Object handleMap(String authorization,
                             String ruleCode,
                             String traceparent,
                             String stickyKey,
                             Map<String, Object> requestBody,
                             ModelApiProtocolEnum protocol,
                             MediaType contentType) {
        // Build request context once so later stages can reuse resolved metadata.
        String apiKey = validateRequestHeaders(authorization, ruleCode);
        GatewayTraceContext traceContext = GatewayTraceContext.currentOrCreate(traceparent);
        GatewayRequestContext context = new GatewayRequestContext(
                authorization, apiKey, ruleCode, traceContext, protocol, stickyKey);
        GatewayExchange exchange = new GatewayExchange(requestBody, contentType);
        try {
            ResolvedRouting routingInfo = resolveRouting(context);
            context.routing(routingInfo);
            dispatchGatewayPlugin(context, exchange, PluginStageCode.GATEWAY_REQUEST, null, null);

            ProxyResult result = proxy(context, exchange);
            exchange.proxyResult(result);
            Object response = applyUsageAccounting(context, exchange);
            // 首 token 响应时间：非流式在此写头（响应体就绪即产出就绪，等价首 token）；
            // 流式返回 StreamingResponseBody，由响应体在上游首行写出前自行写头。
            // 观察者由子类按需提供（如慢信号摘除），两个出口共用同一钩子
            if (response != null && !(response instanceof StreamingResponseBody)) {
                FirstTokenHeaderWriter.of(context, firstTokenObserver(context)).mark();
            }
            dispatchGatewayPlugin(context, exchange, PluginStageCode.GATEWAY_RESPONSE, response, null);
            return response;
        } catch (RuntimeException ex) {
            dispatchGatewayPlugin(context, exchange, PluginStageCode.GATEWAY_ERROR, null, ex);
            throw ex;
        }
    }


    protected abstract ResolvedRouting resolveRouting(GatewayRequestContext context);

    protected abstract ProxyResult proxy(GatewayRequestContext context, GatewayExchange exchange);

    protected abstract Object applyUsageAccounting(GatewayRequestContext context, GatewayExchange exchange);

    /**
     * 首 token 观察者钩子：把「首个可观测产出的耗时（毫秒）」暴露给子类的派生能力
     * （如异常摘除的慢信号计数）。默认无观察者；返回非 null 时，
     * {@link FirstTokenHeaderWriter#mark()} 首次触发即以耗时回调一次（先于写头）。
     *
     * <p>流式与非流式两个出口共用本钩子：流式由 {@code applyUsageAccounting} 构造
     * 写头器时取用，非流式由模板在响应体就绪时取用。</p>
     */
    protected LongConsumer firstTokenObserver(GatewayRequestContext context) {
        return null;
    }

    /**
     * Validates header values after Spring MVC has checked required header presence.
     */
    private String validateRequestHeaders(String authorization, String ruleCode) {
        String apiKey = extractApiKey(authorization);
        if (apiKey == null) {
            throw new GatewayAuthException("Invalid API key");
        }
        if (StringUtils.isBlank(ruleCode)) {
            throw new GatewayInvalidRequestException("Rule header must not be blank");
        }
        return apiKey;
    }

    private String extractApiKey(String authorization) {
        if (!StringUtils.startsWith(authorization, AuthConstants.BEARER_PREFIX)) {
            return null;
        }
        return StringUtils.trimToNull(authorization.substring(AuthConstants.BEARER_PREFIX.length()));
    }

    private void dispatchGatewayPlugin(GatewayRequestContext context,
                                       GatewayExchange exchange,
                                       PluginStageCode stageCode,
                                       Object responseBody,
                                       Throwable error) {
        if (pluginDispatcher == null) {
            return;
        }
        pluginDispatcher.dispatch(
                PluginExtensionPoint.GATEWAY_EXCHANGE,
                stageCode,
                new GatewayPluginContext(
                        context.traceId(),
                        context.traceparent(),
                        stageCode,
                        context.apiKey(),
                        context.ruleCode(),
                        context.protocol() == null ? null : context.protocol().canonical().code(),
                        routingSnapshot(context.routing()),
                        moderationWordResource.currentWords(),
                        exchange == null ? null : exchange.requestBody(),
                        exchange == null ? null : exchange.contentType(),
                        responseBody,
                        error
                )
        );
    }

    private GatewayRoutingSnapshot routingSnapshot(ResolvedRouting routing) {
        if (routing == null) {
            return null;
        }
        String appId = null;
        String department = null;
        if (routing.routingInfo() != null) {
            appId = routing.routingInfo().app() == null ? null : routing.routingInfo().app().appId();
            department = routing.routingInfo().department() == null ? null : routing.routingInfo().department().name();
        }
        return new GatewayRoutingSnapshot(
                routing.ruleId(),
                routing.ruleCode(),
                routing.consumerId(),
                routing.returnUsageDetail(),
                routing.candidateModels() == null ? 0 : routing.candidateModels().size(),
                appId,
                department);
    }
}
