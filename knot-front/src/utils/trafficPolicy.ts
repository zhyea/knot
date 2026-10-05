/** 限流 / 限额策略表单默认值与规范化（与后端 RateLimitPolicy / QuotaPolicy 字段对齐） */

import type {Dict} from "@/types";

/**
 * 限流策略：模型 / 路由规则层
 * - rpm 每分钟请求数，请求进入即计数
 * - tpm 每分钟 token 上限，请求成功后按真实用量累加
 */
export interface RateLimitPolicy {
  rpm: number;
  tpm: number;
}

/**
 * 限额策略：应用 / 供应商账户 / 消费者层
 * - maxTokens 窗口内 token 上限
 * - costLimit 窗口内成本上限（null / 0 表示不限）
 * - currency 成本币种，需与计费结果币种一致才累计
 * - window 统计窗口：MINUTE / HOUR / DAY / WEEK / MONTH
 */
export interface QuotaPolicy {
  maxTokens: number;
  costLimit: number | null;
  currency: string;
  window: string;
}

export const QUOTA_CURRENCY_DEFAULT = "USD";
export const QUOTA_WINDOW_DEFAULT = "MONTH";

export function emptyRateLimitPolicy(): RateLimitPolicy {
  return {rpm: 0, tpm: 0};
}

export function emptyQuotaPolicy(): QuotaPolicy {
  return {
    maxTokens: 0,
    costLimit: null,
    currency: QUOTA_CURRENCY_DEFAULT,
    window: QUOTA_WINDOW_DEFAULT
  };
}

export function normalizeRateLimitPolicy(raw: unknown): RateLimitPolicy {
  if (!raw || typeof raw !== "object") {
    return emptyRateLimitPolicy();
  }
  const source = raw as Dict;
  return {
    rpm: Number(source.rpm) || 0,
    tpm: Number(source.tpm) || 0
  };
}

export function normalizeQuotaPolicy(raw: unknown): QuotaPolicy {
  const empty = emptyQuotaPolicy();
  if (!raw || typeof raw !== "object") {
    return empty;
  }
  const source = raw as Dict;
  const cost = Number(source.costLimit);
  return {
    maxTokens: Number(source.maxTokens) || 0,
    costLimit: Number.isFinite(cost) && cost > 0 ? cost : null,
    currency: String(source.currency ?? "").trim().toUpperCase() || empty.currency,
    window: String(source.window ?? "").trim().toUpperCase() || empty.window
  };
}

export function isEmptyRateLimitPolicy(policy: unknown): boolean {
  const p = normalizeRateLimitPolicy(policy);
  return p.rpm <= 0 && p.tpm <= 0;
}

export function isEmptyQuotaPolicy(policy: unknown): boolean {
  const p = normalizeQuotaPolicy(policy);
  return p.maxTokens <= 0 && !p.costLimit;
}
