package org.chobit.knot.gateway.adapter.model.ref;

/**
 * 适配层自有的计费规则投影：只带计价链路真正消费的字段。
 *
 * <p>2026-10-24 依赖倒挂修复（改进清单 2.5）：适配层不再 import {@code knot-dal} 的
 * {@code BillingRuleEntity}，由网关侧（{@code UsageExtractorRegistry}）与管理端影子链
 * （{@code BillingFactAssembler}）在调用 {@link UsageNormalizationSupport} 前投影。
 * 与 service 侧裁判链的 {@code BillingFactRuleRef} 是两份不同投影：那份供被告链结构化
 * 展示（不带 configJson），本份供计算器解析价格（必须带 configJson）。</p>
 *
 * @param billingMode 计费模式（TOKEN / IMAGE / ...）
 * @param configJson  当前生效版本的价格配置 JSON
 * @param currency    币种
 * @param pricingPlan 进阶定价方案（FIXED / TIERED / PEAK_OFF_PEAK）
 * @param unit        计费单位（1K_TOKENS / 1M_TOKENS / ...）
 * @param versionCode 生效版本码（可空）
 */
public record BillingRuleRef(String billingMode,
                             String configJson,
                             String currency,
                             String pricingPlan,
                             String unit,
                             String versionCode) {
}
