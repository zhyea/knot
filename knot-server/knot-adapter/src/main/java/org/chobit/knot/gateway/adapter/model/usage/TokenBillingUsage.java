package org.chobit.knot.gateway.adapter.model.usage;

import org.apache.commons.lang3.ObjectUtils;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.adapter.usage.UsageRawReader;
import org.chobit.knot.gateway.util.MapNumberUtils;

import java.util.Map;

/**
 * TOKEN 模式计费输入：token 各维度。
 *
 * <p>缓存写两档口径互斥，与对外 {@code ModelUsage.InputTokens} 保持一致：
 * {@code cacheWriteTokens} 是单字段总量口径，{@code cacheWrite5mTokens} / {@code cacheWrite1hTokens}
 * 是按 TTL 拆分的明细口径。只要明细其一大于 0，总量口径即作废（见 {@link #cacheWriteTotal()}）。</p>
 *
 * <p>媒体计量（张数 / 秒数 / 像素）不走这里，由 {@code ImageBillingUsage} /
 * {@code VideoBillingUsage} / {@code AudioBillingUsage} 各自承载。</p>
 */
public record TokenBillingUsage(Long inputTokens,
                                Long outputTokens,
                                Long totalTokens,
                                Long cacheReadTokens,
                                Long cacheWriteTokens,
                                Long cacheWrite5mTokens,
                                Long cacheWrite1hTokens) implements BillingUsage {

    public TokenBillingUsage {
        inputTokens = safe(inputTokens);
        outputTokens = safe(outputTokens);
        totalTokens = safe(totalTokens);
        cacheReadTokens = safe(cacheReadTokens);
        cacheWriteTokens = safe(cacheWriteTokens);
        cacheWrite5mTokens = safe(cacheWrite5mTokens);
        cacheWrite1hTokens = safe(cacheWrite1hTokens);
    }

    public static TokenBillingUsage empty() {
        return new TokenBillingUsage(0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    /**
     * 从上游响应体提取：取 {@code usage} 子对象解析，缺失或非对象时返回空用量。
     *
     * <p>{@code usage} 定位走 {@link UsageRawReader#readUsage}：除顶层外，也下钻
     * Responses 协议事件的 {@code response.usage} 包装。</p>
     */
    public static TokenBillingUsage fromResponse(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return empty();
        }
        Map<String, Object> usage = UsageRawReader.readUsage(body);
        return usage == null ? empty() : from(usage);
    }

    public static TokenBillingUsage from(Map<String, Object> usage) {
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
        return new TokenBillingUsage(
                inputTokens,
                outputTokens,
                totalTokens,
                cacheReadTokens,
                cacheWriteTokens,
                cacheWrite5mTokens,
                cacheWrite1hTokens
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

    @Override
    public TokenBillingUsage tokenUsage() {
        return this;
    }

    /**
     * 把缓存读写加回输入侧总量（Anthropic 口径）。
     *
     * <p>Anthropic 的 {@code input_tokens} 不含缓存读写，两侧相加才是输入侧 token 总量；
     * 缓存写走有效总量（ttl 明细优先），避免 5m/1h 口径下漏算。</p>
     */
    public TokenBillingUsage withCacheCountedInInput() {
        if (isEmpty()) {
            return this;
        }
        long inputTokens = this.inputTokens + cacheReadTokens + cacheWriteTotal();
        long totalTokens = Math.max(this.totalTokens, inputTokens + outputTokens);
        return new TokenBillingUsage(
                inputTokens,
                outputTokens,
                totalTokens,
                cacheReadTokens,
                cacheWriteTokens,
                cacheWrite5mTokens,
                cacheWrite1hTokens
        );
    }

    @Override
    public TokenBillingUsage mergeMax(BillingUsage other) {
        if (!(other instanceof TokenBillingUsage that)) {
            return this;
        }
        return new TokenBillingUsage(
                Math.max(inputTokens, that.inputTokens()),
                Math.max(outputTokens, that.outputTokens()),
                Math.max(totalTokens, that.totalTokens()),
                Math.max(cacheReadTokens, that.cacheReadTokens()),
                Math.max(cacheWriteTokens, that.cacheWriteTokens()),
                Math.max(cacheWrite5mTokens, that.cacheWrite5mTokens()),
                Math.max(cacheWrite1hTokens, that.cacheWrite1hTokens())
        );
    }

    @Override
    public boolean isEmpty() {
        return inputTokens <= 0
                && outputTokens <= 0
                && totalTokens <= 0
                && cacheReadTokens <= 0
                && cacheWriteTokens <= 0
                && cacheWrite5mTokens <= 0
                && cacheWrite1hTokens <= 0;
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
