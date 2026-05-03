package org.chobit.knot.gateway.model;

import java.time.LocalDateTime;

public record HealthResponse(String status, LocalDateTime serverTime, String dbTime) {
}
