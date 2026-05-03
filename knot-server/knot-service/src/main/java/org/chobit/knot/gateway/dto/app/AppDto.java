package org.chobit.knot.gateway.dto.app;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

public record AppDto(Long id, String appId, String name, Long ownerUserId, boolean enabled,
                     RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}
