/** 频控/额度策略表单默认值与规范化（与后端 RateLimitPolicy / QuotaPolicy 字段对齐） */

import type {Dict} from "@/types";

export interface RateLimitPolicy {
  perSecond: number;
  perMinute: number;
  timeWindow: string;
}

export interface QuotaPolicy {
  dailyLimit: number;
  monthlyLimit: number;
  tokenLimit: number;
  /**
   * `emptyQuotaPolicy()` 给 0，`normalizeQuotaPolicy()` 给 boolean。
   * 两种形态在现状里都出现（前者进表单初值，后者来自后端反序列化），故联合；
   * 后端落库前会再走一次 normalize，不一致不会外泄。
   */
  alertEnabled: boolean | number;
}

export function emptyRateLimitPolicy(): RateLimitPolicy {
  return { perSecond: 0, perMinute: 0, timeWindow: "MINUTE" };
}

export function emptyQuotaPolicy(): QuotaPolicy {
  return { dailyLimit: 0, monthlyLimit: 0, tokenLimit: 0, alertEnabled: 0 };
}

export function normalizeRateLimitPolicy(raw: unknown): RateLimitPolicy {
  if (!raw || typeof raw !== "object") {
    return emptyRateLimitPolicy();
  }
  const source = raw as Dict;
  return {
    perSecond: Number(source.perSecond) || 0,
    perMinute: Number(source.perMinute) || 0,
    timeWindow: String(source.timeWindow ?? "").trim() || "MINUTE"
  };
}

export function normalizeQuotaPolicy(raw: unknown): QuotaPolicy {
  if (!raw || typeof raw !== "object") {
    return emptyQuotaPolicy();
  }
  const source = raw as Dict;
  const alert = source.alertEnabled;
  return {
    dailyLimit: Number(source.dailyLimit) || 0,
    monthlyLimit: Number(source.monthlyLimit) || 0,
    tokenLimit: Number(source.tokenLimit) || 0,
    alertEnabled: alert === true || alert === 1 || alert === "1" || alert === "true"
  };
}

export function isEmptyRateLimitPolicy(policy: unknown): boolean {
  const p = normalizeRateLimitPolicy(policy);
  return p.perSecond <= 0 && p.perMinute <= 0;
}

export function isEmptyQuotaPolicy(policy: unknown): boolean {
  const p = normalizeQuotaPolicy(policy);
  return p.dailyLimit <= 0 && p.monthlyLimit <= 0 && p.tokenLimit <= 0;
}
