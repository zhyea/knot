package com.knot.gateway.dto.billing;

public record ReconciliationResultDto(String providerCode, String billDate, int comparedRows, int diffRows, String status) {
}
