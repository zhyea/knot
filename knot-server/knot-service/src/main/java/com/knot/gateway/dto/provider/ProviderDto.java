package com.knot.gateway.dto.provider;

import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;

public record ProviderDto(Long id, String code, String name, String type, String baseUrl, boolean enabled,
                          RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}
