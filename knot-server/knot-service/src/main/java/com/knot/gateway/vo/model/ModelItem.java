package com.knot.gateway.vo.model;

import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;

public record ModelItem(Long id, String name, Long providerId, String modelType, String version, boolean enabled,
                        RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}
