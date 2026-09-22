package org.chobit.knot.gateway.vo.provider;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

import java.time.LocalDateTime;
import java.util.Map;

public record ProviderAccountItem(Long id, Long providerId, String providerName,
                                  String code, String type, String baseUrl, boolean enabled,
                                  LocalDateTime createdAt, LocalDateTime updatedAt,
                                  String credentialType,
                                  Map<String, Object> authConfig,
                                  RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}

