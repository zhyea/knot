package org.chobit.knot.gateway.model;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.chobit.knot.gateway.constants.AiPayloadFields;

import java.util.Map;

public record BillingUsage(Long inputTokens,
                           Long outputTokens,
                           Long totalTokens,
                           Long cacheReadTokens,
                           Long cacheWriteTokens,
                           Long amount) {

    public BillingUsage {
        inputTokens = safe(inputTokens);
        outputTokens = safe(outputTokens);
        totalTokens = safe(totalTokens);
        cacheReadTokens = safe(cacheReadTokens);
        cacheWriteTokens = safe(cacheWriteTokens);
        amount = safe(amount);
    }

    public static BillingUsage empty() {
        return new BillingUsage(0L, 0L, 0L, 0L, 0L, 0L);
    }

    public static BillingUsage from(Map<String, Object> usage) {
        if (usage == null || usage.isEmpty()) {
            return empty();
        }
        long inputTokens = firstLong(usage, AiPayloadFields.PROMPT_TOKENS, AiPayloadFields.INPUT_TOKENS);
        long outputTokens = firstLong(usage, AiPayloadFields.COMPLETION_TOKENS, AiPayloadFields.OUTPUT_TOKENS);
        long totalTokens = firstLong(usage, AiPayloadFields.TOTAL_TOKENS);
        long cacheReadTokens =
                nestedLong(usage, "prompt_tokens_details", "cached_tokens")
                        + nestedLong(usage, "input_tokens_details", "cached_tokens")
                        + firstLong(usage, "cache_read_input_tokens", "cached_read_tokens",
                        "cache_read_tokens", "prompt_cache_hit_tokens");
        long cacheWriteTokens =
                nestedLong(usage, "prompt_tokens_details", "cache_creation_input_tokens")
                        + nestedLong(usage, "input_tokens_details", "cache_creation_input_tokens")
                        + firstLong(usage, "cache_creation_input_tokens", "cached_write_tokens",
                        "cache_write_input_tokens", "cache_write_tokens");
        if (totalTokens <= 0 && (inputTokens > 0 || outputTokens > 0)) {
            totalTokens = inputTokens + outputTokens;
        }
        return new BillingUsage(
                inputTokens,
                outputTokens,
                totalTokens,
                cacheReadTokens,
                cacheWriteTokens,
                firstLong(usage, "image_count", "images", "n", "duration_seconds", "audio_seconds", "video_seconds", "seconds", "amount")
        );
    }

    public BillingUsage mergeMax(BillingUsage other) {
        if (other == null) {
            return this;
        }
        return new BillingUsage(
                Math.max(inputTokens, other.inputTokens()),
                Math.max(outputTokens, other.outputTokens()),
                Math.max(totalTokens, other.totalTokens()),
                Math.max(cacheReadTokens, other.cacheReadTokens()),
                Math.max(cacheWriteTokens, other.cacheWriteTokens()),
                Math.max(amount, other.amount())
        );
    }

    public BillingUsage plus(BillingUsage other) {
        if (other == null) {
            return this;
        }
        return new BillingUsage(
                inputTokens + other.inputTokens(),
                outputTokens + other.outputTokens(),
                totalTokens + other.totalTokens(),
                cacheReadTokens + other.cacheReadTokens(),
                cacheWriteTokens + other.cacheWriteTokens(),
                amount + other.amount()
        );
    }

    public boolean isEmpty() {
        return inputTokens <= 0
                && outputTokens <= 0
                && totalTokens <= 0
                && cacheReadTokens <= 0
                && cacheWriteTokens <= 0
                && amount <= 0;
    }

    private static long safe(Long value) {
        return Math.max(0L, ObjectUtils.defaultIfNull(value, 0L));
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
        if (value instanceof String text && NumberUtils.isCreatable(StringUtils.trim(text))) {
            return NumberUtils.createNumber(StringUtils.trim(text)).longValue();
        }
        return null;
    }
}
