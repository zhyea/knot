package com.knot.gateway.vo.security;

public record SecurityOverview(boolean authEnabled, boolean signVerificationEnabled,
                               int blockedIpCount, int alertCount, double cacheHitRate) {
}
