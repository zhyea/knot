package org.chobit.knot.gateway.model;

public record RateLimitPolicy(int perSecond, int perMinute, String timeWindow) {
}
