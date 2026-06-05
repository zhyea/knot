package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.vo.request.ImageGenerationRequest;
import org.chobit.knot.gateway.vo.request.ImageVariationRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

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
    public Object generations(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
            @RequestBody ImageGenerationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.IMAGE_GENERATIONS);
    }

    /**
     * Handles image edit requests.
     */
    @PostMapping(value = "/edits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object edits(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
            @RequestPart("image") MultipartFile[] image,
            @RequestPart(value = "mask", required = false) MultipartFile mask,
            @RequestParam("prompt") String prompt,
            @RequestParam(value = "background", required = false) String background,
            @RequestParam(value = "input_fidelity", required = false) String inputFidelity,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "n", required = false) Integer n,
            @RequestParam(value = "output_compression", required = false) Integer outputCompression,
            @RequestParam(value = "output_format", required = false) String outputFormat,
            @RequestParam(value = "quality", required = false) String quality,
            @RequestParam(value = "response_format", required = false) String responseFormat,
            @RequestParam(value = "size", required = false) String size,
            @RequestParam(value = "user", required = false) String user) {
        return handleMultipartRequest(authorization, rule, traceparent, imageEditBody(image, mask, prompt,
                background, inputFidelity, model, n, outputCompression, outputFormat, quality, responseFormat, size, user),
                ModelApiProtocolEnum.IMAGE_EDITS);
    }

    /**
     * Handles image variation requests.
     */
    @PostMapping("/variations")
    public Object variations(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
            @RequestBody ImageVariationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.IMAGE_VARIATIONS);
    }

    private Map<String, Object> imageEditBody(MultipartFile[] image,
                                              MultipartFile mask,
                                              String prompt,
                                              String background,
                                              String inputFidelity,
                                              String model,
                                              Integer n,
                                              Integer outputCompression,
                                              String outputFormat,
                                              String quality,
                                              String responseFormat,
                                              String size,
                                              String user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("image", image);
        putIfPresent(body, "mask", mask);
        body.put("prompt", prompt);
        putIfPresent(body, "background", background);
        putIfPresent(body, "input_fidelity", inputFidelity);
        putIfPresent(body, "model", model);
        putIfPresent(body, "n", n);
        putIfPresent(body, "output_compression", outputCompression);
        putIfPresent(body, "output_format", outputFormat);
        putIfPresent(body, "quality", quality);
        putIfPresent(body, "response_format", responseFormat);
        putIfPresent(body, "size", size);
        putIfPresent(body, "user", user);
        return body;
    }

    private void putIfPresent(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }
}
