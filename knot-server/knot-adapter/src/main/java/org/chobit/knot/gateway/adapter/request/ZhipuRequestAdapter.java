package org.chobit.knot.gateway.adapter.request;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.AuthConfigSupport;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.AuthConstants;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ProviderTypeEnum;
import org.chobit.knot.gateway.model.BillingUsage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(20)
public class ZhipuRequestAdapter implements UpstreamRequestAdapter {

    public static final String CODE = "ZHIPU";

    private static final String ROOT_PATH = "/api/paas/v4";
    private static final String CHAT_COMPLETIONS_PATH = ROOT_PATH + "/chat/completions";
    private static final String SHORT_CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String IMAGE_GENERATIONS_PATH = ROOT_PATH + "/images/generations";
    private static final String SHORT_IMAGE_GENERATIONS_PATH = "/images/generations";
    private static final String WATERMARK_ENABLED = "watermark_enabled";
    private static final String USER_ID = "user_id";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Zhipu";
    }

    @Override
    public boolean supports(String providerType) {
        return ProviderTypeEnum.ZHIPU.code().equals(StringUtils.upperCase(StringUtils.trim(providerType)));
    }

    @Override
    public String resolvePath(UpstreamRequestContext context, String defaultPath) {
        if (context.binding() != null && StringUtils.isNotBlank(context.binding().getApiPath())) {
            return StringUtils.trim(context.binding().getApiPath());
        }
        ModelApiProtocolEnum protocol = context.protocol().canonical();
        if (ModelApiProtocolEnum.CHAT_COMPLETIONS == protocol
                || ModelApiProtocolEnum.RESPONSES == protocol
                || ModelApiProtocolEnum.MESSAGES == protocol
                || ModelApiProtocolEnum.COMPLETIONS == protocol) {
            return providerPath(context, CHAT_COMPLETIONS_PATH, SHORT_CHAT_COMPLETIONS_PATH);
        }
        if (ModelApiProtocolEnum.IMAGE_GENERATIONS == protocol) {
            return providerPath(context, IMAGE_GENERATIONS_PATH, SHORT_IMAGE_GENERATIONS_PATH);
        }
        return UpstreamRequestAdapter.super.resolvePath(context, defaultPath);
    }

    @Override
    public Object buildRequestBody(UpstreamRequestContext context) {
        ModelApiProtocolEnum protocol = context.protocol().canonical();
        if (ModelApiProtocolEnum.MESSAGES == protocol) {
            return messagesToChatCompletions(context.requestBody());
        }
        if (ModelApiProtocolEnum.RESPONSES == protocol) {
            return responsesToChatCompletions(context.requestBody());
        }
        if (ModelApiProtocolEnum.COMPLETIONS == protocol) {
            return completionsToChatCompletions(context.requestBody());
        }
        if (ModelApiProtocolEnum.IMAGE_GENERATIONS == protocol) {
            return imageGenerationBody(context);
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

    @Override
    public BillingUsage extractUsageFromBody(Map<String, Object> body) {
        BillingUsage usage = UpstreamRequestAdapter.super.extractUsageFromBody(body);
        if (!usage.isEmpty()) {
            return usage;
        }
        Object data = body.get("data");
        long amount = data instanceof List<?> list ? list.size() : 0L;
        return amount > 0 ? new BillingUsage(0L, 0L, 0L, 0L, 0L, amount) : BillingUsage.empty();
    }

    private String providerPath(UpstreamRequestContext context, String fullPath, String shortPath) {
        String baseUrl = context.baseUrl();
        return StringUtils.contains(StringUtils.trimToEmpty(baseUrl), ROOT_PATH) ? shortPath : fullPath;
    }

    private Map<String, Object> imageGenerationBody(UpstreamRequestContext context) {
        Map<String, Object> source = context.requestBody();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(AiPayloadFields.MODEL, context.model().getModelCode());
        body.put(AiPayloadFields.PROMPT, requiredText(source.get(AiPayloadFields.PROMPT), "prompt is required for Zhipu image generation"));
        copyIfPresent(source, body, "size");
        copyIfPresent(source, body, "quality");
        copyAliasIfPresent(source, body, WATERMARK_ENABLED, "watermark");
        copyAliasIfPresent(source, body, USER_ID, "user");
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> messagesToChatCompletions(Map<String, Object> source) {
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

    private Map<String, Object> responsesToChatCompletions(Map<String, Object> source) {
        Map<String, Object> body = new LinkedHashMap<>(source);
        Object input = source.get(AiPayloadFields.INPUT);
        if (input instanceof List<?>) {
            body.put(AiPayloadFields.MESSAGES, input);
        } else if (input != null) {
            body.put(AiPayloadFields.MESSAGES, List.of(Map.of(
                    AiPayloadFields.ROLE, AiPayloadFields.USER,
                    AiPayloadFields.CONTENT, input
            )));
        } else {
            body.put(AiPayloadFields.MESSAGES, List.of());
        }
        Object instructions = source.get(AiPayloadFields.INSTRUCTIONS);
        if (instructions != null) {
            prependSystemMessage(body, instructions);
        }
        move(body, AiPayloadFields.MAX_OUTPUT_TOKENS, AiPayloadFields.MAX_TOKENS);
        body.remove(AiPayloadFields.INPUT);
        body.remove(AiPayloadFields.INSTRUCTIONS);
        body.remove("previous_response_id");
        body.remove("text");
        body.remove("reasoning");
        body.remove("store");
        return body;
    }

    private Map<String, Object> completionsToChatCompletions(Map<String, Object> source) {
        Map<String, Object> body = new LinkedHashMap<>(source);
        Object prompt = source.get(AiPayloadFields.PROMPT);
        body.put(AiPayloadFields.MESSAGES, List.of(Map.of(
                AiPayloadFields.ROLE, AiPayloadFields.USER,
                AiPayloadFields.CONTENT, prompt == null ? "" : prompt
        )));
        body.remove(AiPayloadFields.PROMPT);
        body.remove("suffix");
        body.remove("best_of");
        body.remove("echo");
        body.remove("logprobs");
        return body;
    }

    @SuppressWarnings("unchecked")
    private void prependSystemMessage(Map<String, Object> body, Object instructions) {
        Object sourceMessages = body.get(AiPayloadFields.MESSAGES);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of(AiPayloadFields.ROLE, AiPayloadFields.SYSTEM, AiPayloadFields.CONTENT, instructions));
        if (sourceMessages instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    messages.add((Map<String, Object>) map);
                }
            }
        }
        body.put(AiPayloadFields.MESSAGES, messages);
    }

    private void move(Map<String, Object> body, String from, String to) {
        if (body.containsKey(from) && !body.containsKey(to)) {
            body.put(to, body.get(from));
        }
        body.remove(from);
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value == null) {
            return;
        }
        if (value instanceof String text && StringUtils.isBlank(text)) {
            return;
        }
        target.put(key, value);
    }

    private void copyAliasIfPresent(Map<String, Object> source, Map<String, Object> target, String key, String alias) {
        if (source.containsKey(key)) {
            copyIfPresent(source, target, key);
            return;
        }
        Object value = source.get(alias);
        if (value == null) {
            return;
        }
        if (value instanceof String text && StringUtils.isBlank(text)) {
            return;
        }
        target.put(key, value);
    }

    private String requiredText(Object value, String message) {
        String text = StringUtils.trimToNull(value == null ? null : String.valueOf(value));
        if (text == null) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }
}
