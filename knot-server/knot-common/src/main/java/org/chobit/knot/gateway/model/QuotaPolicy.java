package org.chobit.knot.gateway.model;

public record QuotaPolicy(long dailyLimit, long monthlyLimit, long tokenLimit, boolean alertEnabled) {
}
