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

@Component
@Order(10)
public class AnthropicProviderAdapter implements UpstreamProviderAdapter {

    private static final String PROVIDER_TYPE = "ANTHROPIC";
    private static final String DEFAULT_VERSION = "2023-06-01";

    private final ProviderCredentialSupport credentialSupport;

    public AnthropicProviderAdapter(ProviderCredentialSupport credentialSupport) {
        this.credentialSupport = credentialSupport;
    }

    @Override
    public boolean supports(String providerType) {
        return providerType != null && PROVIDER_TYPE.equals(providerType.trim().toUpperCase());
    }

    @Override
    public String resolvePath(UpstreamRequestContext context, String defaultPath) {
        if (context.binding() != null && hasText(context.binding().getApiPath())) {
            return context.binding().getApiPath().trim();
        }
        ModelApiProtocol protocol = context.protocol().canonical();
        if (ModelApiProtocol.CHAT_COMPLETIONS == protocol
                || ModelApiProtocol.RESPONSES == protocol
                || ModelApiProtocol.COMPLETIONS == protocol
                || ModelApiProtocol.MESSAGES == protocol) {
            return ModelApiProtocol.MESSAGES.defaultPath();
        }
        return UpstreamProviderAdapter.super.resolvePath(context, defaultPath);
    }

    @Override
    public Map<String, Object> buildRequestBody(UpstreamRequestContext context) {
        ModelApiProtocol protocol = context.protocol().canonical();
        if (ModelApiProtocol.CHAT_COMPLETIONS == protocol) {
            return chatCompletionsToMessages(context.requestBody());
        }
        if (ModelApiProtocol.RESPONSES == protocol) {
            return responsesToMessages(context.requestBody());
        }
        if (ModelApiProtocol.COMPLETIONS == protocol) {
            return completionsToMessages(context.requestBody());
        }
        return context.requestBody();
    }

    @Override
    public void applyHeaders(RestClient.RequestBodySpec requestSpec, UpstreamRequestContext context) {
        String apiKey = credentialValue(context.credential(), "apiKey");
        if (hasText(apiKey)) {
            requestSpec.header("x-api-key", apiKey);
        }
        requestSpec.header("anthropic-version", DEFAULT_VERSION);
    }

    private String credentialValue(ProviderCredentialEntity credential, String key) {
        Object value = credentialSupport.toAuthConfig(credential).get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> chatCompletionsToMessages(Map<String, Object> source) {
        Map<String, Object> body = new LinkedHashMap<>(source);
        List<Map<String, Object>> messages = new ArrayList<>();
        Object sourceMessages = source.get("messages");
        if (sourceMessages instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> message = (Map<String, Object>) map;
                Object role = message.get("role");
                if ("system".equals(role)) {
                    body.putIfAbsent("system", message.get("content"));
                    continue;
                }
                messages.add(message);
            }
        }
        body.put("messages", messages);
        move(body, "max_completion_tokens", "max_tokens");
        move(body, "stop", "stop_sequences");
        body.remove("n");
        body.remove("presence_penalty");
        body.remove("frequency_penalty");
        body.remove("logit_bias");
        body.remove("response_format");
        body.putIfAbsent("max_tokens", 1024);
        return body;
    }

    private Map<String, Object> responsesToMessages(Map<String, Object> source) {
        Map<String, Object> body = new LinkedHashMap<>(source);
        Object input = source.get("input");
        if (input instanceof List<?>) {
            body.put("messages", input);
        } else if (input != null) {
            body.put("messages", List.of(Map.of("role", "user", "content", input)));
        } else {
            body.put("messages", List.of());
        }
        Object instructions = source.get("instructions");
        if (instructions != null) {
            body.put("system", instructions);
        }
        move(body, "max_output_tokens", "max_tokens");
        body.remove("input");
        body.remove("instructions");
        body.remove("previous_response_id");
        body.remove("text");
        body.remove("reasoning");
        body.remove("store");
        body.putIfAbsent("max_tokens", 1024);
        return body;
    }

    private Map<String, Object> completionsToMessages(Map<String, Object> source) {
        Map<String, Object> body = new LinkedHashMap<>(source);
        Object prompt = source.get("prompt");
        body.put("messages", List.of(Map.of("role", "user", "content", prompt == null ? "" : prompt)));
        move(body, "stop", "stop_sequences");
        body.remove("prompt");
        body.remove("suffix");
        body.remove("n");
        body.remove("echo");
        body.remove("logprobs");
        body.remove("presence_penalty");
        body.remove("frequency_penalty");
        body.remove("logit_bias");
        body.putIfAbsent("max_tokens", 1024);
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
