package org.chobit.knot.gateway.vo.billing;

import java.math.BigDecimal;

public record BillingSummary(Long requestCount, Long tokenUsage, BigDecimal totalCost) {
}
