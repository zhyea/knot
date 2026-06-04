package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.vo.request.VideoGenerationRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/openai/videos")
public class GatewayVideoController extends GatewayControllerSupport {

    public GatewayVideoController(GatewayRequestHandler requestHandler) {
        super(requestHandler);
    }

    /**
     * Handles video generation requests.
     */
    @PostMapping("/generations")
    public Object generations(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody VideoGenerationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.VIDEO_GENERATIONS);
    }
}
