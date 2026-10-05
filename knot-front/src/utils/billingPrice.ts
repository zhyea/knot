/**
 * 单价（unitPrice）精度口径：全站唯一出口。
 *
 * <p>后端计费产出成本时用 {@code setScale(8, HALF_UP)}
 * （见 {@code AbstractBillingModeCalculator.cost} 与 {@code BillingService}），
 * 额度计数也按 1e-8 缩放，所以单价的可用精度必须同样到 <b>8 位小数</b>——
 * 否则会出现「单价 1e-8 被输入框截成 0」或「成本上限 8 位、单价只有 4 位」的错配。</p>
 *
 * <p>倍率（multiplier）是 0 &lt; m &lt;= 1 的比例值，2 位小数已足够，不要跟单价对齐。</p>
 */

/** 单价小数位数：8，与计费侧 setScale(8, HALF_UP) 对齐 */
export const UNIT_PRICE_PRECISION = 8;

/** 单价输入步长：10^-4 只是起步增量，不影响可输入的精度（precision 才是硬约束） */
export const UNIT_PRICE_STEP = 0.0001;
