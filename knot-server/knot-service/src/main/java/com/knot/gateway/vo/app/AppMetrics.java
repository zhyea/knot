package com.knot.gateway.vo.app;

public record AppMetrics(Long appInternalId, Long totalRequests, Long successRequests, Long failedRequests, Long tokenUsage) {
}
