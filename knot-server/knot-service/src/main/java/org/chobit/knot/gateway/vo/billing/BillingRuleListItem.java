package org.chobit.knot.gateway.vo.billing;

/**
 * 计费规则列表轻量 VO：字段与列表页展示一一对应，不多查。
 * configJson / uniqHash / 生效期 / 备注 / 绑定模型数等重字段走 GET /api/billing/rules/{id} 详情。
 */
public record BillingRuleListItem(
        Long id,
        String code,
        String modelFamilyCode,
        String modelFamilyName,
        String versionCode,
        String billingMode,
        String pricingPlan,
        String currency,
        String unit,
        boolean enabled
) {
}
