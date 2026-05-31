package org.chobit.knot.gateway.controller;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.model.GatewayResponse;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.vo.request.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class GatewayController {

    private final GatewayRequestHandler requestHandler;

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/chat/completions")
    public ResponseEntity<?> chatCompletions(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION, required = true) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE, required = true) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = true) String traceparent,
            @RequestBody ChatCompletionRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.CHAT_COMPLETIONS);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/responses")
    public ResponseEntity<?> responses(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION, required = true) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE, required = true) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = true) String traceparent,
            @RequestBody ResponseRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.RESPONSES);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/messages")
    public ResponseEntity<?> messages(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION, required = true) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE, required = true) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = true) String traceparent,
            @RequestBody MessageRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.MESSAGES);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/completions")
    public ResponseEntity<?> completions(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION, required = true) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE, required = true) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = true) String traceparent,
            @RequestBody CompletionRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.COMPLETIONS);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/embeddings")
    public ResponseEntity<?> embeddings(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION, required = true) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE, required = true) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = true) String traceparent,
            @RequestBody EmbeddingRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.EMBEDDINGS);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/images/generations")
    public ResponseEntity<?> imageGenerations(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION, required = true) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE, required = true) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = true) String traceparent,
            @RequestBody ImageGenerationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.IMAGE_GENERATIONS);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/images/edits")
    public ResponseEntity<?> imageEdits(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION, required = true) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE, required = true) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = true) String traceparent,
            @RequestBody ImageEditRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.IMAGE_EDITS);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/audio/transcriptions")
    public ResponseEntity<?> audioTranscriptions(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION, required = true) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE, required = true) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = true) String traceparent,
            @RequestBody AudioTranscriptionRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.AUDIO_TRANSCRIPTIONS);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/audio/translations")
    public ResponseEntity<?> audioTranslations(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION, required = true) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE, required = true) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = true) String traceparent,
            @RequestBody AudioTranslationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.AUDIO_TRANSLATIONS);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/audio/speech")
    public ResponseEntity<?> audioSpeech(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION, required = true) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE, required = true) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = true) String traceparent,
            @RequestBody AudioSpeechRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.AUDIO_SPEECH);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/videos/generations")
    public ResponseEntity<?> videoGenerations(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION, required = true) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE, required = true) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = true) String traceparent,
            @RequestBody VideoGenerationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.VIDEO_GENERATIONS);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/rerank")
    public ResponseEntity<?> rerank(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION, required = true) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE, required = true) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = true) String traceparent,
            @RequestBody RerankRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.RERANK);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/moderations")
    public ResponseEntity<?> moderations(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION, required = true) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE, required = true) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = true) String traceparent,
            @RequestBody ModerationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.MODERATIONS);
    }

    private ResponseEntity<?> handleRequest(String authorization,
                                            String rule,
                                            String traceparent,
                                            GatewayModelRequest request,
                                            ModelApiProtocolEnum protocol) {
        GatewayResponse response =
                requestHandler.handle(authorization, rule, traceparent, request, protocol);
        return ResponseEntity.status(response.status()).body(response.body());
    }
}
