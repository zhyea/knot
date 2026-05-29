package org.chobit.knot.gateway.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.service.BillingService;
import org.chobit.knot.gateway.service.ProxyService;
import org.chobit.knot.gateway.service.RateLimitService;
import org.chobit.knot.gateway.service.RoutingAuthService;
import org.chobit.knot.gateway.util.tools.RoutingSecretKeyGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1")
public class GatewayController {

    private final ProxyService proxyService;
    private final RateLimitService rateLimitService;
    private final RoutingAuthService routingAuthService;
    private final BillingService billingService;
    private final ObjectMapper objectMapper;

    public GatewayController(ProxyService proxyService,
                             RateLimitService rateLimitService,
                             RoutingAuthService routingAuthService,
                             BillingService billingService,
                             ObjectMapper objectMapper) {
        this.proxyService = proxyService;
        this.rateLimitService = rateLimitService;
        this.routingAuthService = routingAuthService;
        this.billingService = billingService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/chat/completions")
    public ResponseEntity<?> chatCompletions(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> requestBody) {
        return handleRequest(authorization, requestBody);
    }

    @PostMapping("/completions")
    public ResponseEntity<?> completions(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> requestBody) {
        return handleRequest(authorization, requestBody);
    }

    @PostMapping("/embeddings")
    public ResponseEntity<?> embeddings(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> requestBody) {
        return handleRequest(authorization, requestBody);
    }

    private ResponseEntity<?> handleRequest(String authorization, Map<String, Object> requestBody) {
        String apiKey = extractApiKey(authorization);
        if (apiKey == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", Map.of("message", "Invalid API key", "type", "auth_error")));
        }

        RateLimitService.AppContext appContext;
        RoutingAuthService.ResolvedRouting routing = null;
        if (RoutingSecretKeyGenerator.isRoutingSecretKey(apiKey)) {
            String requestedModel = requestBody.get("model") == null ? null : String.valueOf(requestBody.get("model"));
            routing = routingAuthService.resolve(apiKey, requestedModel);
            if (routing == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", Map.of("message", "Invalid routing secret key", "type", "auth_error")));
            }
            appContext = routing.appContext();
        } else {
            appContext = rateLimitService.resolveApp(apiKey);
            if (appContext == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", Map.of("message", "Invalid API key", "type", "auth_error")));
            }
        }

        ProxyService.ProxyResult result;
        if (routing != null) {
            result = proxyWithFailover(requestBody, appContext, routing);
        } else {
            String model = (String) requestBody.get("model");
            if (!rateLimitService.checkRateLimit(appContext, model)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error", Map.of("message", "Rate limit exceeded", "type", "rate_limit_error")));
            }
            result = proxyService.proxy(requestBody, appContext);
        }

        if (result.errorCode() != null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", Map.of("message", result.errorMessage(), "type", "upstream_error")));
        }

        return ResponseEntity.ok(appendUsageDetailIfRequired(result, routing));
    }

    private ProxyService.ProxyResult proxyWithFailover(Map<String, Object> requestBody,
                                                       RateLimitService.AppContext appContext,
                                                       RoutingAuthService.ResolvedRouting routing) {
        ProxyService.ProxyResult lastResult = null;
        for (RoutingRuleTargetDto candidate : routing.candidateModels()) {
            requestBody.put("model", candidate.targetCode());
            if (!rateLimitService.checkRateLimit(appContext, candidate.targetCode())) {
                return new ProxyService.ProxyResult(null, null, candidate.targetId(),
                        "RATE_LIMIT_EXCEEDED", "Rate limit exceeded");
            }
            lastResult = proxyService.proxy(requestBody, appContext);
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
    private Object appendUsageDetailIfRequired(ProxyService.ProxyResult result, RoutingAuthService.ResolvedRouting routing) {
        if (routing == null || !routing.returnUsageDetail() || result == null || result.responseBody() == null) {
            return result == null ? null : result.responseBody();
        }
        try {
            Map<String, Object> body = objectMapper.readValue(result.responseBody(), new TypeReference<>() {
            });
            Object usageObj = body.get("usage");
            Map<String, Object> usage = usageObj instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            Map<String, Object> billing = billingService.calculateUsageDetail(result.modelId(), usage);
            if (billing != null) {
                body.put("knot_billing", billing);
            }
            return body;
        } catch (Exception ignored) {
            return result.responseBody();
        }
    }

    private String extractApiKey(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String key = authorization.substring(7).trim();
        return key.isEmpty() ? null : key;
    }
}
