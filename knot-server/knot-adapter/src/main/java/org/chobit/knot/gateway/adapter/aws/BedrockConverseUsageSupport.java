package org.chobit.knot.gateway.adapter.aws;

import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.usage.UsageRawReader;
import org.chobit.knot.gateway.util.MapNumberUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bedrock Converse 的用端口径换算：Converse 的 {@code usage} 是 <b>camelCase</b>
 * （{@code inputTokens} / {@code outputTokens} / {@code cacheReadInputTokens} / {@code cacheWriteInputTokens}），
 * 与 OpenAI / Anthropic 的 snake_case 不同。
 *
 * <p>请求适配器（回给客户端的响应）与用量提取器（计费）都要走这套换算，故收口在一处，
 * 避免两处口径漂移。</p>
 *
 * <p><b>待实测（R2）</b>：Converse 的 {@code inputTokens} 是否已含缓存读写——本实现按
 * 「不含」处理（原样上报，不加回输入侧），与 Anthropic 提取器
 * （{@code withCacheCountedInInput}）刻意不同，真实环境实测后再定。</p>
 */
public final class BedrockConverseUsageSupport {

    private static final String INPUT_TOKENS = "inputTokens";
    private static final String OUTPUT_TOKENS = "outputTokens";
    private static final String TOTAL_TOKENS = "totalTokens";
    private static final String CACHE_READ = "cacheReadInputTokens";
    private static final String CACHE_WRITE = "cacheWriteInputTokens";

    private BedrockConverseUsageSupport() {
    }

    /**
     * Converse usage（camelCase）→ OpenAI 口径 usage（snake_case）。
     * 回给客户端的响应必须是 OpenAI 形状，否则下游按 {@code prompt_tokens} 读不到用量。
     */
    public static Map<String, Object> toOpenAiUsage(Object usageNode) {
        if (!(usageNode instanceof Map<?, ?> raw)) {
            return null;
        }
        Map<String, Object> source = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key != null) {
                source.put(String.valueOf(key), value);
            }
        });
        Map<String, Object> usage = new LinkedHashMap<>();
        putLong(usage, AiPayloadFields.PROMPT_TOKENS, MapNumberUtils.firstLong(source, INPUT_TOKENS));
        putLong(usage, AiPayloadFields.COMPLETION_TOKENS, MapNumberUtils.firstLong(source, OUTPUT_TOKENS));
        long total = MapNumberUtils.firstLong(source, TOTAL_TOKENS);
        putLong(usage, AiPayloadFields.TOTAL_TOKENS,
                total > 0 ? total
                        : MapNumberUtils.firstLong(source, INPUT_TOKENS) + MapNumberUtils.firstLong(source, OUTPUT_TOKENS));
        putLong(usage, "cache_read_input_tokens", MapNumberUtils.firstLong(source, CACHE_READ));
        putLong(usage, "cache_write_input_tokens", MapNumberUtils.firstLong(source, CACHE_WRITE));
        return usage;
    }

    /**
     * 从响应体提取计费用量：优先按 Converse 的 camelCase 解析，
     * 全部为 0（说明不是 Converse 口径）时回落通用 {@code TokenBillingUsage} 解析。
     */
    public static TokenBillingUsage fromResponse(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return TokenBillingUsage.empty();
        }
        Map<String, Object> usage = UsageRawReader.readUsage(body);
        if (usage == null || usage.isEmpty()) {
            return TokenBillingUsage.empty();
        }
        long inputTokens = MapNumberUtils.firstLong(usage, INPUT_TOKENS);
        long outputTokens = MapNumberUtils.firstLong(usage, OUTPUT_TOKENS);
        long totalTokens = MapNumberUtils.firstLong(usage, TOTAL_TOKENS);
        long cacheRead = MapNumberUtils.firstLong(usage, CACHE_READ);
        long cacheWrite = MapNumberUtils.firstLong(usage, CACHE_WRITE);
        if (inputTokens == 0 && outputTokens == 0 && totalTokens == 0 && cacheRead == 0 && cacheWrite == 0) {
            // 不是 Converse 口径（可能已转成 OpenAI 形状），交给通用解析
            return TokenBillingUsage.fromResponse(body);
        }
        if (totalTokens <= 0) {
            totalTokens = inputTokens + outputTokens;
        }
        return new TokenBillingUsage(inputTokens, outputTokens, totalTokens, cacheRead, cacheWrite, 0L, 0L);
    }

    private static void putLong(Map<String, Object> target, String key, long value) {
        target.put(key, Math.max(0L, value));
    }
}
