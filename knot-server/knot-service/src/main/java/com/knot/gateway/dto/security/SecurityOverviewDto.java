package com.knot.gateway.dto.security;

public record SecurityOverviewDto(boolean authEnabled, boolean signVerificationEnabled, int blockedIpCount,
                                  int alertCount, double cacheHitRate) {
}
