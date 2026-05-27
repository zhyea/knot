package org.chobit.knot.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 响应体中 Token 消耗取值逻辑（JSONPath / 点路径表达式）。
 * <p>
 * 示例：
 * <pre>{@code
 * {
 *   "usage_path": "$.usage",
 *   "total_tokens": "$.usage.total_tokens",
 *   "cached_read_tokens": "$.usage.prompt_tokens_details.cached_tokens",
 *   "cached_write_tokens": "$.usage.prompt_tokens_details.cache_creation_input_tokens",
 *   "output_tokens": "$.usage.completion_tokens",
 *   "uncached_tokens": "$.usage.prompt_tokens",
 *   "total_input_tokens": "$.usage.input_tokens"
 * }
 * }</pre>
 */
public record UsageExtractConfig(
        @JsonProperty("usage_path") String usagePath,
        @JsonProperty("total_tokens") String totalTokens,
        @JsonProperty("cached_read_tokens") String cachedReadTokens,
        @JsonProperty("cached_write_tokens") String cachedWriteTokens,
        @JsonProperty("output_tokens") String outputTokens,
        @JsonProperty("uncached_tokens") String uncachedTokens,
        @JsonProperty("total_input_tokens") String totalInputTokens
) {
}
