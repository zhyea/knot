package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.service.ProxyService;
import org.chobit.knot.gateway.service.RateLimitService;
import org.chobit.knot.gateway.service.RequestLogService;
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

    public GatewayController(ProxyService proxyService,
                             RateLimitService rateLimitService,
                             RequestLogService requestLogService) {
        this.proxyService = proxyService;
        this.rateLimitService = rateLimitService;
        this.requestLogService = requestLogService;
    }

    /**
     * Chat Completions - OpenAI 兼容接口
     */
    @PostMapping("/chat/completions")
    public ResponseEntity<?> chatCompletions(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> requestBody) {
        return handleRequest(authorization, requestBody);
    }

    /**
     * Completions - OpenAI 兼容接口
     */
    @PostMapping("/completions")
    public ResponseEntity<?> completions(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> requestBody) {
        return handleRequest(authorization, requestBody);
    }

    /**
     * Embeddings - OpenAI 兼容接口
     */
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

        // 1. 鉴权 - 通过 API Key 查找 App
        RateLimitService.AppContext appContext = rateLimitService.resolveApp(apiKey);
        if (appContext == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", Map.of("message", "Invalid API key", "type", "auth_error")));
        }

        // 2. 限流与配额校验
        String model = (String) requestBody.get("model");
        if (!rateLimitService.checkRateLimit(appContext, model)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", Map.of("message", "Rate limit exceeded", "type", "rate_limit_error")));
        }

        // 3. 转发到 Provider
        ProxyService.ProxyResult result = proxyService.proxy(requestBody, appContext);

        long latencyMs = System.currentTimeMillis() - startMs;

        // 4. 记录请求日志
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
