package org.chobit.knot.gateway.vo.billing;

import java.time.LocalDateTime;

/**
 * 计费规则 VO：规则主体绑定信息 + 当前版本（最近生效版本）配置。
 * pricingPlan 为进阶定价方案（FIXED/TIERED...）；价格与阶梯配置全部在 configJson 中（defaultUnitPrice / basePrices / tier）。
 */
public record BillingRule(
        Long id,
        String code,
        String logicalModelCode,
        String logicalModelName,
        String versionCode,
        String uniqHash,
        String billingMode,
        String pricingPlan,
        String currency,
        String unit,
        String configJson,
        boolean enabled,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        String remark
) {
}
