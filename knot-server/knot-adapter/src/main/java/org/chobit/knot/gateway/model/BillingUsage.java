package org.chobit.knot.gateway.model;

import org.apache.commons.lang3.ObjectUtils;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.util.MapNumberUtils;

import java.util.Map;

/**
 * 内部计费输入视图（扁平）：把厂商各异的上游用量压成计费所需的几个维度。
 *
 * <p>缓存写两档口径互斥，与对外 {@code ModelUsage.InputTokens} 保持一致：
 * {@code cacheWriteTokens} 是单字段总量口径，{@code cacheWrite5mTokens} / {@code cacheWrite1hTokens}
 * 是按 TTL 拆分的明细口径。只要明细其一大于 0，总量口径即作废（见 {@link #cacheWriteTotal()}）。
 */
public record BillingUsage(Long inputTokens,
                           Long outputTokens,
                           Long totalTokens,
                           Long cacheReadTokens,
                           Long cacheWriteTokens,
                           Long cacheWrite5mTokens,
                           Long cacheWrite1hTokens,
                           Long amount) {

    public BillingUsage {
        inputTokens = safe(inputTokens);
        outputTokens = safe(outputTokens);
        totalTokens = safe(totalTokens);
        cacheReadTokens = safe(cacheReadTokens);
        cacheWriteTokens = safe(cacheWriteTokens);
        cacheWrite5mTokens = safe(cacheWrite5mTokens);
        cacheWrite1hTokens = safe(cacheWrite1hTokens);
        amount = safe(amount);
    }

    public static BillingUsage empty() {
        return new BillingUsage(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
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
        long cacheWrite5mTokens = cacheWriteTtl(usage, "5m");
        long cacheWrite1hTokens = cacheWriteTtl(usage, "1h");
        if (totalTokens <= 0 && (inputTokens > 0 || outputTokens > 0)) {
            totalTokens = inputTokens + outputTokens;
        }
        return new BillingUsage(
                inputTokens,
                outputTokens,
                totalTokens,
                cacheReadTokens,
                cacheWriteTokens,
                cacheWrite5mTokens,
                cacheWrite1hTokens,
                MapNumberUtils.firstLong(usage, "image_count", "images", "n", "duration_seconds", "audio_seconds", "video_seconds", "seconds", "amount")
        );
    }

    /**
     * 缓存写的有效总量：明细（5m / 1h）优先，只有明细都为 0 时才用单字段总量。
     */
    public long cacheWriteTotal() {
        return cacheWrite5mTokens + cacheWrite1hTokens > 0
                ? cacheWrite5mTokens + cacheWrite1hTokens
                : cacheWriteTokens;
    }

    /** 是否走了 TTL 明细口径（5m / 1h 任一大于 0） */
    public boolean cacheWriteTtlSplit() {
        return cacheWrite5mTokens + cacheWrite1hTokens > 0;
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
                Math.max(cacheWrite5mTokens, other.cacheWrite5mTokens()),
                Math.max(cacheWrite1hTokens, other.cacheWrite1hTokens()),
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
                cacheWrite5mTokens + other.cacheWrite5mTokens(),
                cacheWrite1hTokens + other.cacheWrite1hTokens(),
                amount + other.amount()
        );
    }

    public boolean isEmpty() {
        return inputTokens <= 0
                && outputTokens <= 0
                && totalTokens <= 0
                && cacheReadTokens <= 0
                && cacheWriteTokens <= 0
                && cacheWrite5mTokens <= 0
                && cacheWrite1hTokens <= 0
                && amount <= 0;
    }

    /**
     * 按 TTL 取缓存写 token 数：{@code ttl} 取 {@code 5m} 或 {@code 1h}。
     *
     * <p>别名与 {@code ModelUsageNormalizer} 的 5m/1h 识别保持一致，避免两处口径漂移。</p>
     */
    private static long cacheWriteTtl(Map<String, Object> usage, String ttl) {
        return MapNumberUtils.firstLong(usage,
                        "cache_write_" + ttl,
                        "cache_creation_" + ttl + "_input_tokens",
                        "cache_write_input_tokens_" + ttl)
                + MapNumberUtils.nestedLong(usage, "cache_creation", "ephemeral_" + ttl + "_input_tokens")
                + MapNumberUtils.nestedLong(usage, "cache_creation", "cache_creation_input_tokens_" + ttl)
                + MapNumberUtils.nestedLong(usage, "input_tokens_details", "cache_creation_" + ttl + "_input_tokens")
                + MapNumberUtils.nestedLong(usage, "prompt_tokens_details", "cache_creation_" + ttl + "_input_tokens");
    }

    private static long safe(Long value) {
        return Math.max(0L, ObjectUtils.defaultIfNull(value, 0L));
    }

}
