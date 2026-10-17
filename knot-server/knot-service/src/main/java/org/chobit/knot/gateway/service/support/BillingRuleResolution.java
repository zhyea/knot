package org.chobit.knot.gateway.service.support;

import org.chobit.knot.gateway.dto.routing.BillingRuleSource;
import org.chobit.knot.gateway.entity.BillingRuleEntity;

/**
 * 三级回退解析结果：命中的规则 + 命中层级。
 *
 * @param rule      命中的规则实体（MISSING 时为 null）
 * @param matchedBy 命中层级
 */
public record BillingRuleResolution(BillingRuleEntity rule, BillingRuleSource matchedBy) {

    public static BillingRuleResolution missing() {
        return new BillingRuleResolution(null, BillingRuleSource.MISSING);
    }
}
