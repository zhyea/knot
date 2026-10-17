package org.chobit.knot.gateway.dto.routing;

/**
 * 计费规则快照（被告链依据之一）：当次实际参与计费的规则元数据。
 *
 * <p>{@code matchedBy} 与原 {@link BillingFactSubject#ruleSource()} 同源，二者保持一致；
 * {@code versionCode} 取规则当前生效版本，供排障定位「用的是哪一版单价」。</p>
 *
 * @param code        规则码
 * @param billingMode 计费模式（TOKEN / IMAGE / ...）
 * @param unit        计费单位（1K_TOKENS / 1M_TOKENS / ...）
 * @param currency    币种
 * @param pricingPlan 进阶定价方案（FIXED / TIERED / PEAK_OFF_PEAK）
 * @param matchedBy   命中层级
 * @param versionCode 生效版本码（可空）
 */
public record BillingFactRuleRef(String code,
                                 String billingMode,
                                 String unit,
                                 String currency,
                                 String pricingPlan,
                                 BillingRuleSource matchedBy,
                                 String versionCode) {

    /** 未命中任何规则时的占位快照 */
    public static BillingFactRuleRef absent() {
        return new BillingFactRuleRef(null, null, null, null, null, BillingRuleSource.MISSING, null);
    }

    public boolean missing() {
        return matchedBy == BillingRuleSource.MISSING;
    }
}
