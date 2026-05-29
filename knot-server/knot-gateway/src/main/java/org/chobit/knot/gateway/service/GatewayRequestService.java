package org.chobit.knot.gateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chobit.knot.gateway.constants.ModelApiProtocol;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.vo.request.GatewayModelRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GatewayRequestService {

    private final ProxyService proxyService;
    private final RateLimitService rateLimitService;
    private final RoutingAuthService routingAuthService;
    private final BillingService billingService;
    private final GatewayTrafficGuard trafficGuard;
    private final ObjectMapper objectMapper;

    public GatewayRequestService(ProxyService proxyService,
                                 RateLimitService rateLimitService,
                                 RoutingAuthService routingAuthService,
                                 BillingService billingService,
                                 GatewayTrafficGuard trafficGuard,
                                 ObjectMapper objectMapper) {
        this.proxyService = proxyService;
        this.rateLimitService = rateLimitService;
        this.routingAuthService = routingAuthService;
        this.billingService = billingService;
        this.trafficGuard = trafficGuard;
        this.objectMapper = objectMapper;
    }

    public GatewayResponse handle(String authorization,
                                  String ruleCode,
                                  String traceparent,
                                  GatewayModelRequest request,
                                  ModelApiProtocol protocol) {
        return handle(authorization, ruleCode, traceparent, request.toMap(objectMapper), protocol);
    }

    private GatewayResponse handle(String authorization,
                                   String ruleCode,
                                   String traceparent,
                                   Map<String, Object> requestBody,
                                   ModelApiProtocol protocol) {
        String apiKey = extractApiKey(authorization);
        if (apiKey == null) {
            return error(HttpStatus.UNAUTHORIZED, "Invalid API key", "auth_error");
        }
        if (ruleCode == null || ruleCode.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "Rule header is required", "invalid_request_error");
        }
        if (traceparent == null || traceparent.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "traceparent header is required", "invalid_request_error");
        }

        AuthContext authContext = resolveAuthContext(apiKey, ruleCode);
        if (authContext.error() != null) {
            return authContext.error();
        }

        ProxyService.ProxyResult result = proxy(authContext, requestBody, protocol, traceparent);
        if (result.errorCode() != null) {
            return error(HttpStatus.BAD_GATEWAY, result.errorMessage(), "upstream_error");
        }

        return new GatewayResponse(HttpStatus.OK, applyUsageAccounting(result, authContext.routing()));
    }

    private AuthContext resolveAuthContext(String apiKey, String ruleCode) {
        RoutingAuthService.ResolvedRouting routing = routingAuthService.resolveByRule(apiKey, ruleCode);
        if (routing == null) {
            return AuthContext.error(error(HttpStatus.UNAUTHORIZED, "Invalid API key or routing rule", "auth_error"));
        }
        return AuthContext.success(routing.appContext(), routing);
    }

    private ProxyService.ProxyResult proxy(AuthContext authContext,
                                           Map<String, Object> requestBody,
                                           ModelApiProtocol protocol,
                                           String traceparent) {
        return proxyWithFailover(requestBody, authContext.appContext(), authContext.routing(), protocol, traceparent);
    }

    private ProxyService.ProxyResult proxyWithFailover(Map<String, Object> requestBody,
                                                       RateLimitService.AppContext appContext,
                                                       RoutingAuthService.ResolvedRouting routing,
                                                       ModelApiProtocol protocol,
                                                       String traceparent) {
        ProxyService.ProxyResult lastResult = null;
        for (RoutingRuleTargetDto candidate : routing.candidateModels()) {
            requestBody.put("model", candidate.targetCode());
            if (!rateLimitService.checkRateLimit(appContext, candidate.targetCode())
                    || !trafficGuard.check(routing, candidate)) {
                return new ProxyService.ProxyResult(null, null, candidate.targetId(),
                        "RATE_LIMIT_EXCEEDED", "Rate limit exceeded");
            }
            lastResult = proxyService.proxy(requestBody, appContext, protocol, traceparent);
            if (lastResult.errorCode() == null) {
                return lastResult;
            }
        }
        return lastResult != null
                ? lastResult
                : new ProxyService.ProxyResult(null, null, null,
                "NO_ROUTING_TARGET", "No available routing target");
    }

    @SuppressWarnings("unchecked")
    private Object applyUsageAccounting(ProxyService.ProxyResult result, RoutingAuthService.ResolvedRouting routing) {
        if (routing == null || result == null || result.responseBody() == null) {
            return result == null ? null : result.responseBody();
        }
        try {
            Map<String, Object> body = objectMapper.readValue(result.responseBody(), new TypeReference<>() {
            });
            Object usageObj = body.get("usage");
            Map<String, Object> usage = usageObj instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            Map<String, Object> billing = billingService.calculateUsageDetail(result.modelId(), usage);
            if (!routing.returnUsageDetail()) {
                return result.responseBody();
            }
            if (billing != null) {
                body.put("knot_billing", billing);
                body.put("knot_usage", normalizedUsage(usage, billing));
            }
            return body;
        } catch (Exception ignored) {
            return result.responseBody();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizedUsage(Map<String, Object> usage, Map<String, Object> billing) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        Map<String, Object> billingUsage = billing.get("usage") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        normalized.put("totalTokens", firstLong(billingUsage, "totalTokens") > 0
                ? firstLong(billingUsage, "totalTokens")
                : totalTokens(usage));
        normalized.put("totalCost", billing.get("totalCost"));
        normalized.put("currency", billing.get("currency"));
        normalized.put("billingRuleId", billing.get("billingRuleId"));
        normalized.put("billingRuleCode", billing.get("billingRuleCode"));
        normalized.put("billingVersionNo", billing.get("versionNo"));
        return normalized;
    }

    private long totalTokens(Map<String, Object> usage) {
        long total = firstLong(usage, "total_tokens");
        if (total > 0) {
            return total;
        }
        return firstLong(usage, "prompt_tokens", "input_tokens")
                + firstLong(usage, "completion_tokens", "output_tokens");
    }

    private long firstLong(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Long.parseLong(text.trim());
                } catch (NumberFormatException ignored) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    private GatewayResponse error(HttpStatus status, String message, String type) {
        return new GatewayResponse(status, Map.of("error", Map.of("message", message, "type", type)));
    }

    private String extractApiKey(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String key = authorization.substring(7).trim();
        return key.isEmpty() ? null : key;
    }

    public record GatewayResponse(HttpStatus status, Object body) {
    }

    private record AuthContext(RateLimitService.AppContext appContext,
                               RoutingAuthService.ResolvedRouting routing,
                               GatewayResponse error) {

        private static AuthContext success(RateLimitService.AppContext appContext,
                                           RoutingAuthService.ResolvedRouting routing) {
            return new AuthContext(appContext, routing, null);
        }

        private static AuthContext error(GatewayResponse error) {
            return new AuthContext(null, null, error);
        }
    }
}
