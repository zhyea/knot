package org.chobit.knot.gateway.dto.provider;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

import java.time.LocalDateTime;
import java.util.Map;

public record ProviderAccountDto(Long id, Long providerId, String providerName,
                                 String code, String name, String type, String baseUrl, boolean enabled,
                                 LocalDateTime createdAt, LocalDateTime updatedAt,
                                 String credentialType,
                                 Map<String, Object> authConfig,
                                 RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}
