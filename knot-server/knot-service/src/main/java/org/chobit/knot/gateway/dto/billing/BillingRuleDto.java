package org.chobit.knot.gateway.dto.billing;

import java.time.LocalDateTime;

/**
 * 计费规则 DTO：规则主体绑定信息 + 当前版本（最近生效版本）配置。
 * 价格与阶梯配置全部在 configJson 中（defaultUnitPrice / basePrices / ladder）。
 */
public record BillingRuleDto(
        Long id,
        String code,
        String providerCode,
        String providerName,
        String logicalModelCode,
        String logicalModelName,
        String versionCode,
        String uniqHash,
        String billingMode,
        String currency,
        String unit,
        String configJson,
        boolean enabled,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        String remark
) {
}
