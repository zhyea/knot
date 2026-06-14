package org.chobit.knot.gateway.model;

import java.math.BigDecimal;
import java.util.List;

public record NormalizedBillingAmount(long totalTokens,
                                      BigDecimal totalCost,
                                      List<NormalizedUsageDetail> details) {
}
