package org.chobit.knot.gateway.adapter.request;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.AuthConfigSupport;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.AuthConstants;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Order(100)
@RequiredArgsConstructor
public class OpenAiCompatibleRequestAdapter implements UpstreamRequestAdapter {

    public static final String CODE = "OPENAI_COMPATIBLE";

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = Set.of(
            ModelApiProtocolEnum.CHAT_COMPLETIONS,
            ModelApiProtocolEnum.RESPONSES,
            ModelApiProtocolEnum.MESSAGES,
            ModelApiProtocolEnum.COMPLETIONS,
            ModelApiProtocolEnum.EMBEDDINGS,
            ModelApiProtocolEnum.IMAGE_GENERATIONS,
            ModelApiProtocolEnum.IMAGE_EDITS,
            ModelApiProtocolEnum.IMAGE_VARIATIONS,
            ModelApiProtocolEnum.AUDIO_TRANSCRIPTIONS,
            ModelApiProtocolEnum.AUDIO_TRANSLATIONS,
            ModelApiProtocolEnum.AUDIO_SPEECH,
            ModelApiProtocolEnum.VIDEO_GENERATIONS,
            ModelApiProtocolEnum.RERANK,
            ModelApiProtocolEnum.MODERATIONS,
            ModelApiProtocolEnum.CUSTOM
    );

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "OpenAI Compatible";
    }

    @Override
    public Set<ModelApiProtocolEnum> protocol() {
        return PROTOCOLS;
    }

    @Override
    public String resolvePath(UpstreamRequestContext context, String defaultPath) {
        if (context.binding() != null && StringUtils.isNotBlank(context.binding().getApiPath())) {
            return StringUtils.trim(context.binding().getApiPath());
        }
        if (ModelApiProtocolEnum.MESSAGES == context.protocol().canonical()) {
            return ModelApiProtocolEnum.CHAT_COMPLETIONS.defaultPath();
        }
        return UpstreamRequestAdapter.super.resolvePath(context, defaultPath);
    }

    @Override
    public Object buildRequestBody(UpstreamRequestContext context) {
        if (ModelApiProtocolEnum.MESSAGES == context.protocol().canonical()) {
            return anthropicMessagesToChatCompletions(context.requestBody());
        }
        return context.requestBody();
    }

    @Override
    public void applyHeaders(RestClient.RequestBodySpec requestSpec, UpstreamRequestContext context) {
        String apiKey = AuthConfigSupport.apiKey(context.authConfig());
        if (StringUtils.isNotBlank(apiKey)) {
            requestSpec.header(GatewayHeaders.AUTHORIZATION, AuthConstants.BEARER_PREFIX + apiKey);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> anthropicMessagesToChatCompletions(Map<String, Object> source) {
        Map<String, Object> body = new LinkedHashMap<>(source);
        List<Map<String, Object>> messages = new ArrayList<>();
        Object system = source.get(AiPayloadFields.SYSTEM);
        if (system != null) {
            messages.add(Map.of(AiPayloadFields.ROLE, AiPayloadFields.SYSTEM, AiPayloadFields.CONTENT, system));
        }
        Object sourceMessages = source.get(AiPayloadFields.MESSAGES);
        if (sourceMessages instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    messages.add((Map<String, Object>) map);
                }
            }
        }
        if (!messages.isEmpty()) {
            body.put(AiPayloadFields.MESSAGES, messages);
        }
        move(body, AiPayloadFields.STOP_SEQUENCES, AiPayloadFields.STOP);
        body.remove(AiPayloadFields.SYSTEM);
        body.remove("top_k");
        return body;
    }

    private void move(Map<String, Object> body, String from, String to) {
        if (body.containsKey(from) && !body.containsKey(to)) {
            body.put(to, body.get(from));
        }
        body.remove(from);
    }
}
