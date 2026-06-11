package org.chobit.knot.gateway.adapter.request;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

public interface UpstreamRequestAdapter {

    String code();

    String label();

    boolean supports(String providerType);

    default String resolvePath(UpstreamRequestContext context, String defaultPath) {
        String bindingPath = context.binding() == null ? null : context.binding().getApiPath();
        return StringUtils.defaultIfBlank(StringUtils.trim(bindingPath), defaultPath);
    }

    Object buildRequestBody(UpstreamRequestContext context);

    default MediaType resolveContentType(UpstreamRequestContext context) {
        return context.contentType();
    }

    void applyHeaders(RestClient.RequestBodySpec requestSpec, UpstreamRequestContext context);

    default String handleResponse(String responseBody, UpstreamRequestContext context) {
        return responseBody;
    }

    default BillingUsage extractUsage(String responseBody, UpstreamRequestContext context) {
        if (StringUtils.isBlank(responseBody)) {
            return BillingUsage.empty();
        }
        if (isEventStream(responseBody)) {
            return extractEventStreamUsage(responseBody, context);
        }
        Map<String, Object> body = readMap(responseBody);
        if (body == null || body.isEmpty()) {
            return BillingUsage.empty();
        }
        return extractUsageFromBody(body);
    }

    @SuppressWarnings("unchecked")
    default BillingUsage extractUsageFromBody(Map<String, Object> body) {
        Object usage = body.get(AiPayloadFields.USAGE);
        if (usage instanceof Map<?, ?> map) {
            return BillingUsage.from((Map<String, Object>) map);
        }
        return BillingUsage.empty();
    }

    default BillingUsage extractEventStreamUsage(String responseBody, UpstreamRequestContext context) {
        BillingUsage usage = BillingUsage.empty();
        for (String line : responseBody.split("\\R")) {
            String trimmed = StringUtils.trim(line);
            if (!StringUtils.startsWith(trimmed, "data:")) {
                continue;
            }
            String data = StringUtils.trim(trimmed.substring("data:".length()));
            if (StringUtils.isEmpty(data) || "[DONE]".equals(data)) {
                continue;
            }
            Map<String, Object> event = readMap(data);
            if (event == null || event.isEmpty()) {
                continue;
            }
            usage = usage.mergeMax(extractUsageFromBody(event));
        }
        return usage;
    }

    private static boolean isEventStream(String value) {
        return value.lines().anyMatch(line -> StringUtils.startsWith(StringUtils.trim(line), "data:"));
    }

    private static Map<String, Object> readMap(String json) {
        try {
            ObjectMapper mapper = JsonKit.mapper();
            return mapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return null;
        }
    }
}
