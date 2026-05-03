package com.knot.gateway.dto.app;

import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;

public record AppDto(Long id, String appId, String name, Long ownerUserId, boolean enabled,
                     RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}
