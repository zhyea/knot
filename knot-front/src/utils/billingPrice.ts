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
export const UNIT_PRICE_STEP = 0.01;

/**
 * 「每单位基数」的量纲文案：单位是分母，文案必须跟着变。
 *
 * <p>否则用户会按「每秒」的直觉去填「每百万 Token」的单价，
 * 而视频模式里这两者是<b>同一条规则的两个可选量纲</b>（豆包 Seedance 按 token 计价）。</p>
 */
const UNIT_LABELS: Record<string, string> = {
  PER_SECOND: "每秒单价",
  PER_IMAGE: "每张单价",
  PER_REQUEST: "每次单价",
  PER_TOKEN: "每 Token 单价",
  "1K_TOKENS": "每千 Token 单价",
  "1M_TOKENS": "每百万 Token 单价"
};

/** 单位 -> 量纲文案；未知单位按 fallback（视频按秒是默认量纲） */
export function unitLabelOf(unit?: string | null, fallback = "每秒单价"): string {
  return UNIT_LABELS[String(unit || "")] || fallback;
}

/**
 * 是否为 token 量纲。
 *
 * <p>token 量纲下输入侧分量（秒 / 张）一律不参与计费：一条规则只有一个单位基数（分母），
 * 秒 / 张分量会被同一个分母除错（2026-10-26 对齐豆包口径时确立）。</p>
 */
export function isTokenPricedUnit(unit?: string | null): boolean {
  const value = String(unit || "");
  return value === "1M_TOKENS" || value === "1K_TOKENS" || value === "PER_TOKEN";
}
