package org.chobit.knot.gateway.dto.billing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BillingRuleDto(
        Long id,
        String code,
        String name,
        Long providerId,
        String providerName,
        Long logicalModelId,
        String logicalModelName,
        Long currentVersionId,
        Integer versionNo,
        String versionCode,
        String billingMode,
        String currency,
        String itemType,
        String unit,
        BigDecimal unitPrice,
        String configJson,
        String ladderJson,
        boolean enabled,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        String remark
) {
}
