package com.knot.gateway.vo.provider;

import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;

public record ProviderItem(Long id, String name, String type, String baseUrl, boolean enabled,
                           RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}
