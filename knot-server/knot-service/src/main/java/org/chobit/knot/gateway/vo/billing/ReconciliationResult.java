package org.chobit.knot.gateway.vo.billing;

public record ReconciliationResult(String providerCode, String billDate, int comparedRows, int diffRows, String status) {
}
