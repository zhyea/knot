package com.knot.gateway.dto.billing;

import java.math.BigDecimal;

public record BillingSummaryDto(Long requestCount, Long tokenUsage, BigDecimal totalCost) {
}
