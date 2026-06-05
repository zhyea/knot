package org.chobit.knot.gateway.runtime;

import org.chobit.knot.gateway.constants.AuthConstants;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.vo.request.GatewayModelRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;

import java.util.Map;

/**
 * Handles the incoming request flow. Executes the public operation.
 */
public abstract class AbstractGatewayRequestTemplate {

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
        ResolvedRouting routingInfo = resolveRouting(context);
        context.routing(routingInfo);

        // Proxy the normalized request and then apply usage accounting.
        GatewayExchange exchange = new GatewayExchange(requestBody, contentType);
        ProxyResult result = proxy(context, exchange);

        // Apply usage accounting.
        exchange.proxyResult(result);
        return applyUsageAccounting(context, exchange);
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
}
