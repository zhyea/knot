package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.AuthConstants;
import org.chobit.knot.gateway.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.model.GatewayResponse;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.vo.request.GatewayModelRequest;
import org.springframework.http.HttpStatus;

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
    public final GatewayResponse handle(String authorization,
                                        String ruleCode,
                                        String traceparent,
                                        GatewayModelRequest request,
                                        ModelApiProtocolEnum protocol) {
        // Validate required headers before routing.
        GatewayResponse validation = validateRequestHeaders(authorization, ruleCode, traceparent);
        if (validation != null) {
            return validation;
        }

        // Build request context once so later stages can reuse resolved metadata.
        String apiKey = extractApiKey(authorization);
        GatewayRequestContext context = new GatewayRequestContext(authorization, apiKey, ruleCode, traceparent, protocol);
        ResolvedRouting routingInfo = resolveRouting(context);
        context.routing(routingInfo);

        // Proxy the normalized request and then apply usage accounting.
        Map<String, Object> requestBody = JsonKit.toMap(request);
        GatewayExchange exchange = new GatewayExchange(requestBody);
        ProxyResult result = proxy(context, exchange);

        exchange.proxyResult(result);
        if (result.errorCode() != null) {
            return error(HttpStatus.BAD_GATEWAY, result.errorMessage(), GatewayErrorTypeEnum.UPSTREAM_ERROR.code());
        }
        return new GatewayResponse(HttpStatus.OK, applyUsageAccounting(context, exchange));
    }


    protected abstract ResolvedRouting resolveRouting(GatewayRequestContext context);

    protected abstract ProxyResult proxy(GatewayRequestContext context, GatewayExchange exchange);

    protected abstract Object applyUsageAccounting(GatewayRequestContext context, GatewayExchange exchange);

    protected GatewayResponse error(HttpStatus status, String message, String type) {
        return new GatewayResponse(status, Map.of("error", Map.of("message", message, "type", type)));
    }

    private GatewayResponse validateRequestHeaders(String authorization, String ruleCode, String traceparent) {
        if (extractApiKey(authorization) == null) {
            return error(HttpStatus.UNAUTHORIZED, "Invalid API key", GatewayErrorTypeEnum.AUTH_ERROR.code());
        }
        if (ruleCode == null || ruleCode.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "Rule header is required", GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code());
        }
        if (traceparent == null || traceparent.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "traceparent header is required", GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code());
        }
        return null;
    }

    private String extractApiKey(String authorization) {
        if (authorization == null || !authorization.startsWith(AuthConstants.BEARER_PREFIX)) {
            return null;
        }
        String key = authorization.substring(AuthConstants.BEARER_PREFIX.length()).trim();
        return key.isEmpty() ? null : key;
    }
}
