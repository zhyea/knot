package org.chobit.knot.gateway.model;

public record TrafficPolicies(RateLimitPolicy rateLimitPolicy,
                              QuotaPolicy quotaPolicy) {
}
