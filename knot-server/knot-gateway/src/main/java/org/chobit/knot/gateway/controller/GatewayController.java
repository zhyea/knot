package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.service.ProxyService;
import org.chobit.knot.gateway.service.RateLimitService;
import org.chobit.knot.gateway.service.RequestLogService;
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
    private final RequestLogService requestLogService;
    private final RoutingAuthService routingAuthService;

    public GatewayController(ProxyService proxyService,
                             RateLimitService rateLimitService,
                             RequestLogService requestLogService,
                             RoutingAuthService routingAuthService) {
        this.proxyService = proxyService;
        this.rateLimitService = rateLimitService;
        this.requestLogService = requestLogService;
        this.routingAuthService = routingAuthService;
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
        long startMs = System.currentTimeMillis();
        String apiKey = extractApiKey(authorization);
        if (apiKey == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", Map.of("message", "Invalid API key", "type", "auth_error")));
        }

        RateLimitService.AppContext appContext;
        if (RoutingSecretKeyGenerator.isRoutingSecretKey(apiKey)) {
            String requestedModel = requestBody.get("model") == null ? null : String.valueOf(requestBody.get("model"));
            RoutingAuthService.ResolvedRouting routing = routingAuthService.resolve(apiKey, requestedModel);
            if (routing == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", Map.of("message", "Invalid routing secret key", "type", "auth_error")));
            }
            appContext = routing.appContext();
            if (requestBody.get("model") == null || String.valueOf(requestBody.get("model")).isBlank()) {
                requestBody.put("model", routing.primaryModel().modelCode());
            }
        } else {
            appContext = rateLimitService.resolveApp(apiKey);
            if (appContext == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", Map.of("message", "Invalid API key", "type", "auth_error")));
            }
        }

        String model = (String) requestBody.get("model");
        if (!rateLimitService.checkRateLimit(appContext, model)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", Map.of("message", "Rate limit exceeded", "type", "rate_limit_error")));
        }

        ProxyService.ProxyResult result = proxyService.proxy(requestBody, appContext);

        long latencyMs = System.currentTimeMillis() - startMs;
        requestLogService.log(appContext, result, latencyMs);

        if (result.errorCode() != null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", Map.of("message", result.errorMessage(), "type", "upstream_error")));
        }

        return ResponseEntity.ok(result.responseBody());
    }

    private String extractApiKey(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String key = authorization.substring(7).trim();
        return key.isEmpty() ? null : key;
    }
}
