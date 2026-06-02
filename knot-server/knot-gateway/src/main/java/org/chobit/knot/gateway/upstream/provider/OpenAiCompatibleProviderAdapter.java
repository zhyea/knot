package org.chobit.knot.gateway.upstream.provider;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.AuthConstants;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ProviderTypeEnum;
import org.chobit.knot.gateway.entity.ProviderCredentialEntity;
import org.chobit.knot.gateway.service.ProviderCredentialSupport;
import org.chobit.knot.gateway.upstream.UpstreamRequestContext;
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
public class OpenAiCompatibleProviderAdapter implements UpstreamProviderAdapter {

    private static final Set<String> PROVIDER_TYPES = Set.of(
            ProviderTypeEnum.OPENAI.code(),
            ProviderTypeEnum.DEEPSEEK.code(),
            ProviderTypeEnum.OPENROUTER.code()
    );

    private final ProviderCredentialSupport credentialSupport;

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public boolean supports(String providerType) {
        return providerType == null || PROVIDER_TYPES.contains(providerType.trim().toUpperCase());
    }

    /**
     * Resolves the requested value from current context and configuration. Executes the public operation.
     */
    @Override
    public String resolvePath(UpstreamRequestContext context, String defaultPath) {
        if (context.binding() != null && hasText(context.binding().getApiPath())) {
            return context.binding().getApiPath().trim();
        }
        if (ModelApiProtocolEnum.MESSAGES == context.protocol().canonical()) {
            return ModelApiProtocolEnum.CHAT_COMPLETIONS.defaultPath();
        }
        return UpstreamProviderAdapter.super.resolvePath(context, defaultPath);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public Map<String, Object> buildRequestBody(UpstreamRequestContext context) {
        if (ModelApiProtocolEnum.MESSAGES == context.protocol().canonical()) {
            return anthropicMessagesToChatCompletions(context.requestBody());
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
            requestSpec.header(GatewayHeaders.AUTHORIZATION, AuthConstants.BEARER_PREFIX + apiKey);
        }
    }

    private String credentialValue(ProviderCredentialEntity credential, String key) {
        Object value = credentialSupport.toAuthConfig(credential).get(key);
        return value == null ? null : String.valueOf(value).trim();
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
