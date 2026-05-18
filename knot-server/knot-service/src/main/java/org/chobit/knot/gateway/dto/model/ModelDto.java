package org.chobit.knot.gateway.dto.model;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

public record ModelDto(Long id, String modelCode, String name, Long providerId, String providerName, String modelType,
                       String version, boolean enabled, RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}
