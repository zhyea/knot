package org.chobit.knot.gateway.dto.model;

public record ModelTestResultDto(String output, int latencyMs, int tokenUsage) {
}
