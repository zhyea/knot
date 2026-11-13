package org.chobit.knot.svc.vo.gateway;

import java.time.LocalDateTime;

/**
 * 网关配置快照：计费规则（计价模式、定价方案与生效区间）。
 * 字段逐字平移 {@code BillingRuleEntity}，不裁剪；新增实体字段时 {@code GatewayConfigConverter} 必须同步。
 */
public record BillingRuleConfigVo(
        Long id,
        String code,
        String modelFamilyCode,
        String modelFamilyName,
        Integer status,
        Integer isDeleted,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String versionCode,
        String uniqHash,
        Integer versionStatus,
        String billingMode,
        String pricingPlan,
        String currency,
        String unit,
        String configJson,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Long boundModelCount
) {
}
