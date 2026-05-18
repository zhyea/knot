/** 频控/额度策略表单默认值与规范化（与后端 RateLimitPolicy / QuotaPolicy 字段对齐） */

export function emptyRateLimitPolicy() {
  return { perSecond: 0, perMinute: 0, timeWindow: "MINUTE" };
}

export function emptyQuotaPolicy() {
  return { dailyLimit: 0, monthlyLimit: 0, tokenLimit: 0, alertEnabled: 0 };
}

export function normalizeRateLimitPolicy(raw) {
  if (!raw || typeof raw !== "object") {
    return emptyRateLimitPolicy();
  }
  return {
    perSecond: Number(raw.perSecond) || 0,
    perMinute: Number(raw.perMinute) || 0,
    timeWindow: raw.timeWindow?.trim() || "MINUTE"
  };
}

export function normalizeQuotaPolicy(raw) {
  if (!raw || typeof raw !== "object") {
    return emptyQuotaPolicy();
  }
  const alert = raw.alertEnabled;
  return {
    dailyLimit: Number(raw.dailyLimit) || 0,
    monthlyLimit: Number(raw.monthlyLimit) || 0,
    tokenLimit: Number(raw.tokenLimit) || 0,
    alertEnabled: alert === true || alert === 1 || alert === "1" || alert === "true"
  };
}

export function isEmptyRateLimitPolicy(policy) {
  const p = normalizeRateLimitPolicy(policy);
  return p.perSecond <= 0 && p.perMinute <= 0;
}

export function isEmptyQuotaPolicy(policy) {
  const p = normalizeQuotaPolicy(policy);
  return p.dailyLimit <= 0 && p.monthlyLimit <= 0 && p.tokenLimit <= 0;
}
