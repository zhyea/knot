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
    public Object transcriptions(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
            @RequestBody AudioTranscriptionRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.AUDIO_TRANSCRIPTIONS);
    }

    /**
     * Handles audio translation requests.
     */
    @PostMapping("/translations")
    public Object translations(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
            @RequestBody AudioTranslationRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.AUDIO_TRANSLATIONS);
    }

    /**
     * Handles audio speech synthesis requests.
     */
    @PostMapping("/speech")
    public Object speech(
            @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = GatewayHeaders.RULE) String rule,
            @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
            @RequestBody AudioSpeechRequest requestBody) {
        return handleRequest(authorization, rule, traceparent, requestBody, ModelApiProtocolEnum.AUDIO_SPEECH);
    }
}
