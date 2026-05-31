package org.chobit.knot.gateway.upstream;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.AuthConstants;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ProviderTypeEnum;
import org.chobit.knot.gateway.entity.ProviderCredentialEntity;
import org.chobit.knot.gateway.service.ProviderCredentialSupport;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(10)
@RequiredArgsConstructor
public class AnthropicProviderAdapter implements UpstreamProviderAdapter {

    private static final String DEFAULT_VERSION = "2023-06-01";

    private final ProviderCredentialSupport credentialSupport;

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public boolean supports(String providerType) {
        return providerType != null && ProviderTypeEnum.ANTHROPIC.code().equals(providerType.trim().toUpperCase());
    }

    /**
     * Resolves the requested value from current context and configuration. Executes the public operation.
     */
    @Override
    public String resolvePath(UpstreamRequestContext context, String defaultPath) {
        if (context.binding() != null && hasText(context.binding().getApiPath())) {
            return context.binding().getApiPath().trim();
        }
        ModelApiProtocolEnum protocol = context.protocol().canonical();
        if (ModelApiProtocolEnum.CHAT_COMPLETIONS == protocol
                || ModelApiProtocolEnum.RESPONSES == protocol
                || ModelApiProtocolEnum.COMPLETIONS == protocol
                || ModelApiProtocolEnum.MESSAGES == protocol) {
            return ModelApiProtocolEnum.MESSAGES.defaultPath();
        }
        return UpstreamProviderAdapter.super.resolvePath(context, defaultPath);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public Map<String, Object> buildRequestBody(UpstreamRequestContext context) {
        ModelApiProtocolEnum protocol = context.protocol().canonical();
        if (ModelApiProtocolEnum.CHAT_COMPLETIONS == protocol) {
            return chatCompletionsToMessages(context.requestBody());
        }
        if (ModelApiProtocolEnum.RESPONSES == protocol) {
            return responsesToMessages(context.requestBody());
        }
        if (ModelApiProtocolEnum.COMPLETIONS == protocol) {
            return completionsToMessages(context.requestBody());
        }
        return context.requestBody();
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public void applyHeaders(RestClient.RequestBodySpec requestSpec, UpstreamRequestContext context) {
        String apiKey = credentialValue(context.credential(), AuthConstants.API_KEY);
        if (hasText(apiKey)) {
            requestSpec.header(GatewayHeaders.X_API_KEY, apiKey);
        }
        requestSpec.header(GatewayHeaders.ANTHROPIC_VERSION, DEFAULT_VERSION);
    }

    private String credentialValue(ProviderCredentialEntity credential, String key) {
        Object value = credentialSupport.toAuthConfig(credential).get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> chatCompletionsToMessages(Map<String, Object> source) {
        Map<String, Object> body = new LinkedHashMap<>(source);
        List<Map<String, Object>> messages = new ArrayList<>();
        Object sourceMessages = source.get(AiPayloadFields.MESSAGES);
        if (sourceMessages instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> message = (Map<String, Object>) map;
                Object role = message.get(AiPayloadFields.ROLE);
                if (AiPayloadFields.SYSTEM.equals(role)) {
                    body.putIfAbsent(AiPayloadFields.SYSTEM, message.get(AiPayloadFields.CONTENT));
                    continue;
                }
                messages.add(message);
            }
        }
        body.put(AiPayloadFields.MESSAGES, messages);
        move(body, AiPayloadFields.MAX_COMPLETION_TOKENS, AiPayloadFields.MAX_TOKENS);
        move(body, AiPayloadFields.STOP, AiPayloadFields.STOP_SEQUENCES);
        body.remove("n");
        body.remove("presence_penalty");
        body.remove("frequency_penalty");
        body.remove("logit_bias");
        body.remove("response_format");
        body.putIfAbsent(AiPayloadFields.MAX_TOKENS, 1024);
        return body;
    }

    private Map<String, Object> responsesToMessages(Map<String, Object> source) {
        Map<String, Object> body = new LinkedHashMap<>(source);
        Object input = source.get(AiPayloadFields.INPUT);
        if (input instanceof List<?>) {
            body.put(AiPayloadFields.MESSAGES, input);
        } else if (input != null) {
            body.put(AiPayloadFields.MESSAGES, List.of(Map.of(AiPayloadFields.ROLE, AiPayloadFields.USER, AiPayloadFields.CONTENT, input)));
        } else {
            body.put(AiPayloadFields.MESSAGES, List.of());
        }
        Object instructions = source.get(AiPayloadFields.INSTRUCTIONS);
        if (instructions != null) {
            body.put(AiPayloadFields.SYSTEM, instructions);
        }
        move(body, AiPayloadFields.MAX_OUTPUT_TOKENS, AiPayloadFields.MAX_TOKENS);
        body.remove(AiPayloadFields.INPUT);
        body.remove(AiPayloadFields.INSTRUCTIONS);
        body.remove("previous_response_id");
        body.remove("text");
        body.remove("reasoning");
        body.remove("store");
        body.putIfAbsent(AiPayloadFields.MAX_TOKENS, 1024);
        return body;
    }

    private Map<String, Object> completionsToMessages(Map<String, Object> source) {
        Map<String, Object> body = new LinkedHashMap<>(source);
        Object prompt = source.get(AiPayloadFields.PROMPT);
        body.put(AiPayloadFields.MESSAGES, List.of(Map.of(AiPayloadFields.ROLE, AiPayloadFields.USER, AiPayloadFields.CONTENT, prompt == null ? "" : prompt)));
        move(body, AiPayloadFields.STOP, AiPayloadFields.STOP_SEQUENCES);
        body.remove(AiPayloadFields.PROMPT);
        body.remove("suffix");
        body.remove("n");
        body.remove("echo");
        body.remove("logprobs");
        body.remove("presence_penalty");
        body.remove("frequency_penalty");
        body.remove("logit_bias");
        body.putIfAbsent(AiPayloadFields.MAX_TOKENS, 1024);
        return body;
    }

    private void move(Map<String, Object> body, String from, String to) {
        if (body.containsKey(from) && !body.containsKey(to)) {
            body.put(to, body.get(from));
        }
        body.remove(from);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
