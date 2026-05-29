package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.constants.ModelApiProtocol;
import org.chobit.knot.gateway.service.GatewayRequestService;
import org.chobit.knot.gateway.vo.request.AudioSpeechRequest;
import org.chobit.knot.gateway.vo.request.AudioTranscriptionRequest;
import org.chobit.knot.gateway.vo.request.AudioTranslationRequest;
import org.chobit.knot.gateway.vo.request.ChatCompletionRequest;
import org.chobit.knot.gateway.vo.request.CompletionRequest;
import org.chobit.knot.gateway.vo.request.EmbeddingRequest;
import org.chobit.knot.gateway.vo.request.GatewayModelRequest;
import org.chobit.knot.gateway.vo.request.ImageEditRequest;
import org.chobit.knot.gateway.vo.request.ImageGenerationRequest;
import org.chobit.knot.gateway.vo.request.MessageRequest;
import org.chobit.knot.gateway.vo.request.ModerationRequest;
import org.chobit.knot.gateway.vo.request.RerankRequest;
import org.chobit.knot.gateway.vo.request.ResponseRequest;
import org.chobit.knot.gateway.vo.request.VideoGenerationRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class GatewayController {

    private final GatewayRequestService gatewayRequestService;

    public GatewayController(GatewayRequestService gatewayRequestService) {
        this.gatewayRequestService = gatewayRequestService;
    }

    @PostMapping("/chat/completions")
    public ResponseEntity<?> chatCompletions(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Rule") String rule,
            @RequestHeader("traceparent") String traceparent,
            @RequestBody ChatCompletionRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocol.CHAT_COMPLETIONS);
    }

    @PostMapping("/responses")
    public ResponseEntity<?> responses(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Rule") String rule,
            @RequestHeader("traceparent") String traceparent,
            @RequestBody ResponseRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocol.RESPONSES);
    }

    @PostMapping("/messages")
    public ResponseEntity<?> messages(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Rule") String rule,
            @RequestHeader("traceparent") String traceparent,
            @RequestBody MessageRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocol.MESSAGES);
    }

    @PostMapping("/completions")
    public ResponseEntity<?> completions(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Rule") String rule,
            @RequestHeader("traceparent") String traceparent,
            @RequestBody CompletionRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocol.COMPLETIONS);
    }

    @PostMapping("/embeddings")
    public ResponseEntity<?> embeddings(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Rule") String rule,
            @RequestHeader("traceparent") String traceparent,
            @RequestBody EmbeddingRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocol.EMBEDDINGS);
    }

    @PostMapping("/images/generations")
    public ResponseEntity<?> imageGenerations(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Rule") String rule,
            @RequestHeader("traceparent") String traceparent,
            @RequestBody ImageGenerationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocol.IMAGE_GENERATIONS);
    }

    @PostMapping("/images/edits")
    public ResponseEntity<?> imageEdits(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Rule") String rule,
            @RequestHeader("traceparent") String traceparent,
            @RequestBody ImageEditRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocol.IMAGE_EDITS);
    }

    @PostMapping("/audio/transcriptions")
    public ResponseEntity<?> audioTranscriptions(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Rule") String rule,
            @RequestHeader("traceparent") String traceparent,
            @RequestBody AudioTranscriptionRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocol.AUDIO_TRANSCRIPTIONS);
    }

    @PostMapping("/audio/translations")
    public ResponseEntity<?> audioTranslations(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Rule") String rule,
            @RequestHeader("traceparent") String traceparent,
            @RequestBody AudioTranslationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocol.AUDIO_TRANSLATIONS);
    }

    @PostMapping("/audio/speech")
    public ResponseEntity<?> audioSpeech(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Rule") String rule,
            @RequestHeader("traceparent") String traceparent,
            @RequestBody AudioSpeechRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocol.AUDIO_SPEECH);
    }

    @PostMapping("/videos/generations")
    public ResponseEntity<?> videoGenerations(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Rule") String rule,
            @RequestHeader("traceparent") String traceparent,
            @RequestBody VideoGenerationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocol.VIDEO_GENERATIONS);
    }

    @PostMapping("/rerank")
    public ResponseEntity<?> rerank(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Rule") String rule,
            @RequestHeader("traceparent") String traceparent,
            @RequestBody RerankRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocol.RERANK);
    }

    @PostMapping("/moderations")
    public ResponseEntity<?> moderations(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Rule") String rule,
            @RequestHeader("traceparent") String traceparent,
            @RequestBody ModerationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocol.MODERATIONS);
    }

    private ResponseEntity<?> handleRequest(String authorization,
                                            String rule,
                                            String traceparent,
                                            GatewayModelRequest request,
                                            ModelApiProtocol protocol) {
        GatewayRequestService.GatewayResponse response = gatewayRequestService.handle(authorization, rule, traceparent, request, protocol);
        return ResponseEntity.status(response.status()).body(response.body());
    }
}
