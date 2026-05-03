package com.knot.gateway.vo.app;

import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;

public record AppItem(Long id, String appId, String name, Long owner, boolean enabled,
                      RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}
