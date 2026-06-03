package org.chobit.knot.gateway.model;

import java.math.BigDecimal;
import java.util.List;

public record NormalizedUsage(Long totalTokens,
                              BigDecimal totalCost,
                              String currency,
                              String billingVersion,
                              List<NormalizedUsageDetail> detail) {
}
