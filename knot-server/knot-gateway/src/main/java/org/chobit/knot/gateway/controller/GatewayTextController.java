package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.vo.request.ChatCompletionRequest;
import org.chobit.knot.gateway.vo.request.CompletionRequest;
import org.chobit.knot.gateway.vo.request.ResponseRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public Object chatCompletions(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
            @RequestBody ChatCompletionRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.CHAT_COMPLETIONS);
    }

    /**
     * Handles OpenAI-compatible responses requests.
     */
    @PostMapping("/responses")
    public Object responses(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
            @RequestBody ResponseRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.RESPONSES);
    }

    /**
     * Handles OpenAI-compatible legacy completion requests.
     */
    @PostMapping("/completions")
    public Object completions(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
            @RequestBody CompletionRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.COMPLETIONS);
    }
}
