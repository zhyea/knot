package org.chobit.knot.common.model;

import java.time.LocalDateTime;

public record HealthResponse(String status, LocalDateTime serverTime, String dbTime) {
}
