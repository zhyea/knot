package org.chobit.knot.adapter.model.ref;

/**
 * 适配层自有的计费规则投影：只带计价链路真正消费的字段。
 *
 * @param billingMode 计费模式（TOKEN / IMAGE / ...）
 * @param configJson  当前生效版本的价格配置 JSON
 * @param currency    币种
 * @param pricingPlan 进阶定价方案（FIXED / TIERED / PEAK_OFF_PEAK）
 * @param unit        计费单位（1K_TOKENS / 1M_TOKENS / ...）
 * @param versionCode 生效版本码（可空）
 * @param ruleCode    规则业务码，供数仓按「哪条规则算的」归因（可空）
 */
public record BillingRuleRef(String billingMode,
                             String configJson,
                             String currency,
                             String pricingPlan,
                             String unit,
                             String versionCode,
                             String ruleCode) {

    /** 无规则业务码的投影（管理端影子链 / 测试）：归因留空，不影响计价。 */
    public BillingRuleRef(String billingMode, String configJson, String currency,
                          String pricingPlan, String unit, String versionCode) {
        this(billingMode, configJson, currency, pricingPlan, unit, versionCode, null);
    }
}
