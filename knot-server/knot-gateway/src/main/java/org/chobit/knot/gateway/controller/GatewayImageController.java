package org.chobit.knot.gateway.controller;

import org.chobit.knot.common.constants.GatewayHeaders;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.model.vo.request.ImageGenerationRequest;
import org.chobit.knot.gateway.model.vo.request.ImageVariationRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/openai/v1/images")
public class GatewayImageController extends GatewayControllerSupport {

    public GatewayImageController(GatewayRequestHandler requestHandler) {
        super(requestHandler);
    }

    /**
     * Handles image generation requests.
     */
    @PostMapping("/generations")
    public Mono<ResponseEntity<Object>> generations(
        @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
        @RequestHeader(value = GatewayHeaders.RULE) String rule,
        @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
        @RequestHeader(value = GatewayHeaders.X_KNOT_STICKY_KEY, required = false) String stickyKey,
        @RequestBody ImageGenerationRequest requestBody,
        ServerWebExchange exchange) {
        return handleRequest(authorization, rule, traceparent, stickyKey, requestBody, ModelApiProtocolEnum.IMAGE_GENERATIONS, exchange);
    }

    /**
     * Handles image variation requests.
     */
    @PostMapping("/variations")
    public Mono<ResponseEntity<Object>> variations(
        @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
        @RequestHeader(value = GatewayHeaders.RULE) String rule,
        @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
        @RequestHeader(value = GatewayHeaders.X_KNOT_STICKY_KEY, required = false) String stickyKey,
        @RequestBody ImageVariationRequest requestBody,
        ServerWebExchange exchange) {
        return handleRequest(authorization, rule, traceparent, stickyKey, requestBody, ModelApiProtocolEnum.IMAGE_VARIATIONS, exchange);
    }
}

