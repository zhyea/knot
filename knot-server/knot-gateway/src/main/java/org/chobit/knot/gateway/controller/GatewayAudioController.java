package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.vo.request.AudioSpeechRequest;
import org.chobit.knot.gateway.vo.request.AudioTranscriptionRequest;
import org.chobit.knot.gateway.vo.request.AudioTranslationRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequestMapping("/openai/v1/audio")
public class GatewayAudioController extends GatewayControllerSupport {

    public GatewayAudioController(GatewayRequestHandler requestHandler) {
        super(requestHandler);
    }

    /**
     * Handles audio transcription requests.
     */
    @PostMapping("/transcriptions")
    public DeferredResult<Object> transcriptions(
        @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
        @RequestHeader(value = GatewayHeaders.RULE) String rule,
        @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
        @RequestHeader(value = GatewayHeaders.X_KNOT_STICKY_KEY, required = false) String stickyKey,
        @RequestBody AudioTranscriptionRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, stickyKey, requestBody, ModelApiProtocolEnum.AUDIO_TRANSCRIPTIONS);
    }

    /**
     * Handles audio translation requests.
     */
    @PostMapping("/translations")
    public DeferredResult<Object> translations(
        @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
        @RequestHeader(value = GatewayHeaders.RULE) String rule,
        @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
        @RequestHeader(value = GatewayHeaders.X_KNOT_STICKY_KEY, required = false) String stickyKey,
        @RequestBody AudioTranslationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, stickyKey, requestBody, ModelApiProtocolEnum.AUDIO_TRANSLATIONS);
    }

    /**
     * Handles audio speech synthesis requests.
     */
    @PostMapping("/speech")
    public DeferredResult<Object> speech(
        @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
        @RequestHeader(value = GatewayHeaders.RULE) String rule,
        @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
        @RequestHeader(value = GatewayHeaders.X_KNOT_STICKY_KEY, required = false) String stickyKey,
        @RequestBody AudioSpeechRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, stickyKey, requestBody, ModelApiProtocolEnum.AUDIO_SPEECH);
    }
}
