package org.chobit.knot.gateway.dto.billing;

import java.time.LocalDateTime;

/**
 * 计费规则 DTO：规则主体绑定信息 + 当前版本（最近生效版本）配置。
 * pricingPlan 为进阶定价方案（FIXED/TIERED...）；价格与阶梯配置全部在 configJson 中（defaultUnitPrice / basePrices / tier）。
 */
public record BillingRuleDto(
        Long id,
        String code,
        String modelFamilyCode,
        String modelFamilyName,
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
        String remark,
        /** 只读派生：绑定了该规则编码的供应商模型数 */
        Long boundModelCount
) {
    /**
     * 返回「计费字段已归一化」的副本，其余字段沿用原值。
     * 用于版本指纹等只比较版本内容的场景，不落库、不改变原 DTO。
     * 复制逻辑放在本 record 内，避免在调用方按位置罗列全部组件。
     */
    public BillingRuleDto withNormalizedPricing(String billingMode,
                                                String pricingPlan,
                                                String currency,
                                                String unit) {
        return new BillingRuleDto(
                id,
                code,
                modelFamilyCode,
                modelFamilyName,
                versionCode,
                uniqHash,
                billingMode,
                pricingPlan,
                currency,
                unit,
                configJson,
                enabled,
                effectiveFrom,
                effectiveTo,
                remark,
                boundModelCount);
    }
}
