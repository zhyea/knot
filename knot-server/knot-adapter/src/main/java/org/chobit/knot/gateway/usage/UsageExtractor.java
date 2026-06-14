package org.chobit.knot.gateway.usage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.usage.calculator.BillingModeCalculator;
import org.chobit.knot.gateway.util.JsonKit;

import java.util.Map;

public interface UsageExtractor {

    String code();

    String label();

    default boolean streamSupported() {
        return true;
    }

    BillingModeCalculator calculator();

    BillingUsage extractUsage(Map<String, Object> body);

    default BillingUsage extractUsageBody(String responseBody) {
        if (StringUtils.isBlank(responseBody)) {
            return BillingUsage.empty();
        }
        if (isEventStream(responseBody)) {
            return extractEventStreamUsage(responseBody);
        }
        Map<String, Object> body = readMap(responseBody);
        return body == null || body.isEmpty() ? BillingUsage.empty() : extractUsage(body);
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
