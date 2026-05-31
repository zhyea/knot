package org.chobit.knot.gateway.model;

import java.math.BigDecimal;

public record BillingDetail(Long modelId,
                            Long billingRuleId,
                            String billingRuleCode,
                            String billingRuleName,
                            Integer versionNo,
                            String billingMode,
                            String currency,
                            String itemType,
                            String unit,
                            Integer unitSize,
                            BigDecimal unitPrice,
                            BillingUsage usage,
                            BigDecimal totalCost) {
}
