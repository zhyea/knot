package org.chobit.knot.gateway.model;

import org.chobit.knot.gateway.constants.AiPayloadFields;

import java.util.Map;

public record BillingUsage(Long inputTokens,
                           Long outputTokens,
                           Long totalTokens,
                           Long cacheReadTokens,
                           Long amount) {

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static BillingUsage from(Map<String, Object> usage) {
        if (usage == null || usage.isEmpty()) {
            return new BillingUsage(0L, 0L, 0L, 0L, 0L);
        }
        return new BillingUsage(
                firstLong(usage, AiPayloadFields.PROMPT_TOKENS, AiPayloadFields.INPUT_TOKENS),
                firstLong(usage, AiPayloadFields.COMPLETION_TOKENS, AiPayloadFields.OUTPUT_TOKENS),
                firstLong(usage, AiPayloadFields.TOTAL_TOKENS),
                nestedLong(usage, "prompt_tokens_details", "cached_tokens")
                        + nestedLong(usage, "input_tokens_details", "cached_tokens"),
                firstLong(usage, "image_count", "images", "n", "duration_seconds", "audio_seconds", "video_seconds", "seconds", "amount")
        );
    }

    private static long firstLong(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            Long parsed = longValue(value);
            if (parsed != null && parsed > 0) {
                return parsed;
            }
        }
        return 0L;
    }

    @SuppressWarnings("unchecked")
    private static long nestedLong(Map<String, Object> map, String objectKey, String valueKey) {
        Object nested = map.get(objectKey);
        if (!(nested instanceof Map<?, ?> nestedMap)) {
            return 0L;
        }
        Object value = ((Map<String, Object>) nestedMap).get(valueKey);
        Long parsed = longValue(value);
        return parsed == null ? 0L : parsed;
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
