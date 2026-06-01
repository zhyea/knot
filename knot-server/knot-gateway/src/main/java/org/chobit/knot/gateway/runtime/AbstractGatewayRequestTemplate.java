package org.chobit.knot.gateway.runtime;

import org.chobit.knot.gateway.constants.AuthConstants;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.vo.request.GatewayModelRequest;

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
        // Build request context once so later stages can reuse resolved metadata.
        String apiKey = validateRequestHeaders(authorization, ruleCode, traceparent);
        GatewayRequestContext context = new GatewayRequestContext(authorization, apiKey, ruleCode, traceparent, protocol);
        ResolvedRouting routingInfo = resolveRouting(context);
        context.routing(routingInfo);

        // Proxy the normalized request and then apply usage accounting.
        Map<String, Object> requestBody = JsonKit.toMap(request);
        GatewayExchange exchange = new GatewayExchange(requestBody);
        ProxyResult result = proxy(context, exchange);

        exchange.proxyResult(result);
        return applyUsageAccounting(context, exchange);
    }



    protected abstract ResolvedRouting resolveRouting(GatewayRequestContext context);

    protected abstract ProxyResult proxy(GatewayRequestContext context, GatewayExchange exchange);

    protected abstract Object applyUsageAccounting(GatewayRequestContext context, GatewayExchange exchange);

    /**
     * Validates header values after Spring MVC has checked required header presence.
     */
    private String validateRequestHeaders(String authorization, String ruleCode, String traceparent) {
        String apiKey = extractApiKey(authorization);
        if (apiKey == null) {
            throw new GatewayAuthException("Invalid API key");
        }
        if (isBlank(ruleCode)) {
            throw new GatewayInvalidRequestException("Rule header must not be blank");
        }
        if (isBlank(traceparent)) {
            throw new GatewayInvalidRequestException("traceparent header must not be blank");
        }
        return apiKey;
    }

    private String extractApiKey(String authorization) {
        if (authorization == null || !authorization.startsWith(AuthConstants.BEARER_PREFIX)) {
            return null;
        }
        String key = authorization.substring(AuthConstants.BEARER_PREFIX.length()).trim();
        return key.isEmpty() ? null : key;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
