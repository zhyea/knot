package org.chobit.knot.gateway.model;

import java.math.BigDecimal;

public record NormalizedUsage(Long totalTokens,
                              BigDecimal totalCost,
                              String currency,
                              Long billingRuleId,
                              String billingRuleCode,
                              Integer billingVersionNo) {
}
