package org.chobit.knot.gateway.vo.provider;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

public record ProviderItem(Long id, String name, String type, String baseUrl, boolean enabled,
                           RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}
