package org.chobit.knot.gateway.vo.provider;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

import java.time.LocalDateTime;

public record ProviderItem(Long id, String name, String type, String baseUrl, boolean enabled,
                           RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy,
                           Long lastOperatorId, String lastOperatorName, LocalDateTime lastModifiedAt) {
}
