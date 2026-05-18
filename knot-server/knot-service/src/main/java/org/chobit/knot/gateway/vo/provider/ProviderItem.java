package org.chobit.knot.gateway.vo.provider;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

import java.util.Map;

public record ProviderItem(Long id, String code, String name, String type, String baseUrl, boolean enabled,
                           Map<String, Object> authConfig,
                           RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}
