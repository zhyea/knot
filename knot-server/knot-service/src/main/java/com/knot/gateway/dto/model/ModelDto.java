package com.knot.gateway.dto.model;

import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;

public record ModelDto(Long id, String modelCode, String name, Long providerId, String modelType, String version,
                       boolean enabled, RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}
