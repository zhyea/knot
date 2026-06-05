package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.vo.request.ModerationRequest;
import org.chobit.knot.gateway.vo.request.RerankRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class GatewayUtilityController extends GatewayControllerSupport {

    public GatewayUtilityController(GatewayRequestHandler requestHandler) {
        super(requestHandler);
    }

    /**
     * Handles rerank requests.
     */
    @PostMapping("/rerank")
    public Object rerank(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
            @RequestBody RerankRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.RERANK);
    }

    /**
     * Handles moderation requests.
     */
    @PostMapping("/openai/v1/moderations")
    public Object moderations(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
            @RequestBody ModerationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.MODERATIONS);
    }
}
