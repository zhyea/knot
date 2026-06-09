package org.chobit.knot.gateway.runtime;

import org.chobit.knot.gateway.constants.AuthConstants;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.plugin.PluginDispatcher;
import org.chobit.knot.gateway.plugin.PluginExtensionPoint;
import org.chobit.knot.gateway.plugin.PluginStageCode;
import org.chobit.knot.gateway.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.vo.request.GatewayModelRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles the incoming request flow. Executes the public operation.
 */
public abstract class AbstractGatewayRequestTemplate {

    private final PluginDispatcher pluginDispatcher;

    protected AbstractGatewayRequestTemplate(PluginDispatcher pluginDispatcher) {
        this.pluginDispatcher = pluginDispatcher;
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
                               GatewayModelRequest request,
                               ModelApiProtocolEnum protocol) {
        return handleMap(authorization, ruleCode, traceparent, JsonKit.toMap(request), protocol, MediaType.APPLICATION_JSON);
    }

    /**
     * Gateway request template entrypoint for multipart requests.
     */
    public final Object handleMultipart(String authorization,
                                        String ruleCode,
                                        String traceparent,
                                        Map<String, Object> requestBody,
                                        ModelApiProtocolEnum protocol) {
        return handleMap(authorization, ruleCode, traceparent, requestBody, protocol, MediaType.MULTIPART_FORM_DATA);
    }

    private Object handleMap(String authorization,
                             String ruleCode,
                             String traceparent,
                             Map<String, Object> requestBody,
                             ModelApiProtocolEnum protocol,
                             MediaType contentType) {
        // Build request context once so later stages can reuse resolved metadata.
        String apiKey = validateRequestHeaders(authorization, ruleCode);
        GatewayTraceContext traceContext = GatewayTraceContext.currentOrCreate(traceparent);
        GatewayRequestContext context = new GatewayRequestContext(authorization, apiKey, ruleCode, traceContext, protocol);
        GatewayExchange exchange = new GatewayExchange(requestBody, contentType);
        try {
            ResolvedRouting routingInfo = resolveRouting(context);
            context.routing(routingInfo);
            dispatchGatewayPlugin(context, exchange, PluginStageCode.GATEWAY_REQUEST, null, null);

            ProxyResult result = proxy(context, exchange);
            exchange.proxyResult(result);
            Object response = applyUsageAccounting(context, exchange);
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
                        exchange == null ? null : exchange.requestBody(),
                        exchange == null ? null : exchange.contentType(),
                        responseBody,
                        error
                )
        );
    }

    private Map<String, Object> routingSnapshot(ResolvedRouting routing) {
        if (routing == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ruleId", routing.ruleId());
        snapshot.put("ruleCode", routing.ruleCode());
        snapshot.put("consumerId", routing.consumerId());
        snapshot.put("returnUsageDetail", routing.returnUsageDetail());
        snapshot.put("candidateCount", routing.candidateModels() == null ? 0 : routing.candidateModels().size());
        if (routing.routingInfo() != null) {
            snapshot.put("appId", routing.routingInfo().app() == null ? null : routing.routingInfo().app().appId());
            snapshot.put("department", routing.routingInfo().department() == null ? null : routing.routingInfo().department().name());
        }
        return snapshot;
    }
}
