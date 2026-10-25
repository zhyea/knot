package org.chobit.knot.gateway.controller;

import org.chobit.knot.common.constants.GatewayHeaders;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.model.vo.request.ChatCompletionRequest;
import org.chobit.knot.gateway.model.vo.request.CompletionRequest;
import org.chobit.knot.gateway.model.vo.request.ResponseRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/openai/v1")
public class GatewayTextController extends GatewayControllerSupport {

    public GatewayTextController(GatewayRequestHandler requestHandler) {
        super(requestHandler);
    }

    /**
     * Handles OpenAI-compatible chat completion requests.
     */
    @PostMapping("/chat/completions")
    public Mono<ResponseEntity<Object>> chatCompletions(
        @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
        @RequestHeader(value = GatewayHeaders.RULE) String rule,
        @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
        @RequestHeader(value = GatewayHeaders.X_KNOT_STICKY_KEY, required = false) String stickyKey,
        @RequestBody ChatCompletionRequest requestBody,
        ServerWebExchange exchange) {
        return handleRequest(authorization, rule, traceparent, stickyKey, requestBody, ModelApiProtocolEnum.CHAT_COMPLETIONS, exchange);
    }

    /**
     * Handles OpenAI-compatible responses requests.
     */
    @PostMapping("/responses")
    public Mono<ResponseEntity<Object>> responses(
        @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
        @RequestHeader(value = GatewayHeaders.RULE) String rule,
        @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
        @RequestHeader(value = GatewayHeaders.X_KNOT_STICKY_KEY, required = false) String stickyKey,
        @RequestBody ResponseRequest requestBody,
        ServerWebExchange exchange) {
        return handleRequest(authorization, rule, traceparent, stickyKey, requestBody, ModelApiProtocolEnum.RESPONSES, exchange);
    }

    /**
     * Handles OpenAI-compatible legacy completion requests.
     */
    @PostMapping("/completions")
    public Mono<ResponseEntity<Object>> completions(
        @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
        @RequestHeader(value = GatewayHeaders.RULE) String rule,
        @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
        @RequestHeader(value = GatewayHeaders.X_KNOT_STICKY_KEY, required = false) String stickyKey,
        @RequestBody CompletionRequest requestBody,
        ServerWebExchange exchange) {
        return handleRequest(authorization, rule, traceparent, stickyKey, requestBody, ModelApiProtocolEnum.COMPLETIONS, exchange);
    }
}
