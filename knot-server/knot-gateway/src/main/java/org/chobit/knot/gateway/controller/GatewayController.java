package org.chobit.knot.gateway.controller;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.model.GatewayResponse;
import org.chobit.knot.gateway.service.GatewayRequestService;
import org.chobit.knot.gateway.vo.request.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class GatewayController {

    private final GatewayRequestService gatewayRequestService;

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/chat/completions")
    public ResponseEntity<?> chatCompletions(
            @RequestHeader(GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(GatewayHeaders.RULE) String rule,
            @RequestHeader(GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody ChatCompletionRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.CHAT_COMPLETIONS);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/responses")
    public ResponseEntity<?> responses(
            @RequestHeader(GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(GatewayHeaders.RULE) String rule,
            @RequestHeader(GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody ResponseRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.RESPONSES);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/messages")
    public ResponseEntity<?> messages(
            @RequestHeader(GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(GatewayHeaders.RULE) String rule,
            @RequestHeader(GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody MessageRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.MESSAGES);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/completions")
    public ResponseEntity<?> completions(
            @RequestHeader(GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(GatewayHeaders.RULE) String rule,
            @RequestHeader(GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody CompletionRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.COMPLETIONS);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/embeddings")
    public ResponseEntity<?> embeddings(
            @RequestHeader(GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(GatewayHeaders.RULE) String rule,
            @RequestHeader(GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody EmbeddingRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.EMBEDDINGS);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/images/generations")
    public ResponseEntity<?> imageGenerations(
            @RequestHeader(GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(GatewayHeaders.RULE) String rule,
            @RequestHeader(GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody ImageGenerationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.IMAGE_GENERATIONS);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/images/edits")
    public ResponseEntity<?> imageEdits(
            @RequestHeader(GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(GatewayHeaders.RULE) String rule,
            @RequestHeader(GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody ImageEditRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.IMAGE_EDITS);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/audio/transcriptions")
    public ResponseEntity<?> audioTranscriptions(
            @RequestHeader(GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(GatewayHeaders.RULE) String rule,
            @RequestHeader(GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody AudioTranscriptionRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.AUDIO_TRANSCRIPTIONS);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/audio/translations")
    public ResponseEntity<?> audioTranslations(
            @RequestHeader(GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(GatewayHeaders.RULE) String rule,
            @RequestHeader(GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody AudioTranslationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.AUDIO_TRANSLATIONS);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/audio/speech")
    public ResponseEntity<?> audioSpeech(
            @RequestHeader(GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(GatewayHeaders.RULE) String rule,
            @RequestHeader(GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody AudioSpeechRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.AUDIO_SPEECH);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/videos/generations")
    public ResponseEntity<?> videoGenerations(
            @RequestHeader(GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(GatewayHeaders.RULE) String rule,
            @RequestHeader(GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody VideoGenerationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.VIDEO_GENERATIONS);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/rerank")
    public ResponseEntity<?> rerank(
            @RequestHeader(GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(GatewayHeaders.RULE) String rule,
            @RequestHeader(GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody RerankRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.RERANK);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/moderations")
    public ResponseEntity<?> moderations(
            @RequestHeader(GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(GatewayHeaders.RULE) String rule,
            @RequestHeader(GatewayHeaders.TRACEPARENT) String traceparent,
            @RequestBody ModerationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.MODERATIONS);
    }

    private ResponseEntity<?> handleRequest(String authorization,
                                            String rule,
                                            String traceparent,
                                            GatewayModelRequest request,
                                            ModelApiProtocolEnum protocol) {
        GatewayResponse response =
                gatewayRequestService.handle(authorization, rule, traceparent, request, protocol);
        return ResponseEntity.status(response.status()).body(response.body());
    }
}
