package org.chobit.knot.gateway.dto.provider;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

public record ProviderDto(Long id, String code, String name, String type, String baseUrl, boolean enabled,
                          RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}
