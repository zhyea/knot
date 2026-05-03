package com.knot.gateway.dto.app;

public record AppMetricsDto(Long appInternalId, Long totalRequests, Long successRequests, Long failedRequests, Long tokenUsage) {
}
