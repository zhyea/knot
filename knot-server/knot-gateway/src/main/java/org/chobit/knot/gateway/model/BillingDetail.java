package org.chobit.knot.gateway.model;

import java.math.BigDecimal;
import java.util.List;

public record BillingDetail(Long modelId,
                            Long billingRuleId,
                            String billingRuleCode,
                            String billingRuleName,
                            Integer versionNo,
                            String versionCode,
                            String billingMode,
                            String currency,
                            String itemType,
                            String unit,
                            Integer unitSize,
                            BigDecimal unitPrice,
                            BillingUsage usage,
                            List<NormalizedUsageDetail> usageDetails,
                            BigDecimal totalCost) {
}
