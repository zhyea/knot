package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.vo.request.ImageEditRequest;
import org.chobit.knot.gateway.vo.request.ImageGenerationRequest;
import org.chobit.knot.gateway.vo.request.ImageVariationRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/images")
public class GatewayImageController extends GatewayControllerSupport {

    public GatewayImageController(GatewayRequestHandler requestHandler) {
        super(requestHandler);
    }

    /**
     * Handles image generation requests.
     */
    @PostMapping("/generations")
    public Object generations(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody ImageGenerationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.IMAGE_GENERATIONS);
    }

    /**
     * Handles image edit requests.
     */
    @PostMapping("/edits")
    public Object edits(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody ImageEditRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.IMAGE_EDITS);
    }

    /**
     * Handles image variation requests.
     */
    @PostMapping("/variations")
    public Object variations(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody ImageVariationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.IMAGE_VARIATIONS);
    }
}
