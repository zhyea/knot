package org.chobit.knot.gateway.usage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.util.JsonKit;

import java.util.Map;

public interface UsageExtractor {

    String code();

    String label();

    default boolean streamSupported() {
        return true;
    }

    BillingUsage extractUsage(Map<String, Object> body);

    default NormalizedUsage extract(Map<String, Object> body, BillingRuleEntity billingRule) {
        return UsageNormalizationSupport.normalize(extractUsage(body), billingRule);
    }

    default NormalizedUsage extract(String responseBody, BillingRuleEntity billingRule) {
        if (StringUtils.isBlank(responseBody)) {
            return null;
        }
        if (isEventStream(responseBody)) {
            return extractEventStream(responseBody, billingRule);
        }
        Map<String, Object> body = readMap(responseBody);
        return body == null || body.isEmpty() ? null : extract(body, billingRule);
    }

    default NormalizedUsage extractEventStream(String responseBody, BillingRuleEntity billingRule) {
        return UsageNormalizationSupport.normalize(extractEventStreamUsage(responseBody), billingRule);
    }

    default BillingUsage extractEventStreamUsage(String responseBody) {
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
            usage = usage.mergeMax(extractUsage(event));
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
