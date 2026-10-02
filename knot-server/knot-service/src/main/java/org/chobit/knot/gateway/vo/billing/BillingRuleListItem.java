package org.chobit.knot.gateway.vo.billing;

/**
 * 计费规则列表轻量 VO：字段与列表页展示一一对应，不多查。
 * configJson / uniqHash / 生效期 / 备注等重字段走 GET /api/billing/rules/{id} 详情。
 * boundModelCount 为列表展示所需的绑定数量（供应商模型绑定数），由查询侧相关子查询派生。
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
        Long boundModelCount,
        boolean enabled
) {
}
