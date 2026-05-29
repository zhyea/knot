package org.chobit.knot.gateway.service.upstream;

import org.chobit.knot.gateway.constants.ModelApiProtocol;
import org.chobit.knot.gateway.entity.ProviderCredentialEntity;
import org.chobit.knot.gateway.service.ProviderCredentialSupport;
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
public class OpenAiCompatibleProviderAdapter implements UpstreamProviderAdapter {

    private static final Set<String> PROVIDER_TYPES = Set.of("OPENAI", "DEEPSEEK", "OPENROUTER");

    private final ProviderCredentialSupport credentialSupport;

    public OpenAiCompatibleProviderAdapter(ProviderCredentialSupport credentialSupport) {
        this.credentialSupport = credentialSupport;
    }

    @Override
    public boolean supports(String providerType) {
        return providerType == null || PROVIDER_TYPES.contains(providerType.trim().toUpperCase());
    }

    @Override
    public String resolvePath(UpstreamRequestContext context, String defaultPath) {
        if (context.binding() != null && hasText(context.binding().getApiPath())) {
            return context.binding().getApiPath().trim();
        }
        if (ModelApiProtocol.MESSAGES == context.protocol().canonical()) {
            return ModelApiProtocol.CHAT_COMPLETIONS.defaultPath();
        }
        return UpstreamProviderAdapter.super.resolvePath(context, defaultPath);
    }

    @Override
    public Map<String, Object> buildRequestBody(UpstreamRequestContext context) {
        if (ModelApiProtocol.MESSAGES == context.protocol().canonical()) {
            return anthropicMessagesToChatCompletions(context.requestBody());
        }
        return context.requestBody();
    }

    @Override
    public void applyHeaders(RestClient.RequestBodySpec requestSpec, UpstreamRequestContext context) {
        String apiKey = credentialValue(context.credential(), "apiKey");
        if (hasText(apiKey)) {
            requestSpec.header("Authorization", "Bearer " + apiKey);
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
        Object system = source.get("system");
        if (system != null) {
            messages.add(Map.of("role", "system", "content", system));
        }
        Object sourceMessages = source.get("messages");
        if (sourceMessages instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    messages.add((Map<String, Object>) map);
                }
            }
        }
        if (!messages.isEmpty()) {
            body.put("messages", messages);
        }
        move(body, "stop_sequences", "stop");
        body.remove("system");
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
