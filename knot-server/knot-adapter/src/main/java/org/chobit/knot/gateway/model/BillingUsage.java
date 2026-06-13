package org.chobit.knot.gateway.model;

import org.apache.commons.lang3.ObjectUtils;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.util.MapNumberUtils;

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
        long inputTokens = MapNumberUtils.firstLong(usage, AiPayloadFields.PROMPT_TOKENS, AiPayloadFields.INPUT_TOKENS);
        long outputTokens = MapNumberUtils.firstLong(usage, AiPayloadFields.COMPLETION_TOKENS, AiPayloadFields.OUTPUT_TOKENS);
        long totalTokens = MapNumberUtils.firstLong(usage, AiPayloadFields.TOTAL_TOKENS);
        long cacheReadTokens =
                MapNumberUtils.nestedLong(usage, "prompt_tokens_details", "cached_tokens")
                        + MapNumberUtils.nestedLong(usage, "input_tokens_details", "cached_tokens")
                        + MapNumberUtils.firstLong(usage, "cache_read_input_tokens", "cached_read_tokens",
                        "cache_read_tokens", "prompt_cache_hit_tokens");
        long cacheWriteTokens =
                MapNumberUtils.nestedLong(usage, "prompt_tokens_details", "cache_creation_input_tokens")
                        + MapNumberUtils.nestedLong(usage, "input_tokens_details", "cache_creation_input_tokens")
                        + MapNumberUtils.firstLong(usage, "cache_creation_input_tokens", "cached_write_tokens",
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
                MapNumberUtils.firstLong(usage, "image_count", "images", "n", "duration_seconds", "audio_seconds", "video_seconds", "seconds", "amount")
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

}
