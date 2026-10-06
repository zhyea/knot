/**
 * 失败重试策略：默认值与规范化（Mirror 后端 RetryPolicy / org.chobit.knot.gateway.model.RetryPolicy）
 *
 * 重试位于「跨候选 failover」之前：同一路由目标连续失败先在原地重投，次数耗尽才切下一个候选。
 * 字段口径与后端紧凑构造器的归一化保持一致——缺省填充、越界收敛、状态集过滤非法项。
 */

import type {Dict} from "@/types";

export interface RetryPolicy {
  /** 关闭等价于「每个目标只试一次」，直接走 failover */
  enabled: boolean;
  /** 总尝试次数（含首次）；<= 1 表示不重试 */
  maxAttempts: number;
  /** 首次退避基数（毫秒） */
  backoffBaseMs: number;
  /** 单次退避上限（毫秒），同时作为总预算上限 */
  backoffMaxMs: number;
  /** 指数退避基数 */
  multiplier: number;
  /** 退避区间内随机取值，打散重试尖峰 */
  jitter: boolean;
  /** 服从上游 Retry-After（429/503） */
  respectRetryAfter: boolean;
  /** allowlist：命中才重试；denylist：命中才不重试 */
  retryOnMode: string;
  /** 参与判定的上游 HTTP 状态码 */
  retryOn: string[];
}

export const RETRY_MAX_ATTEMPTS_DEFAULT = 3;
export const RETRY_MAX_ATTEMPTS_LIMIT = 10;
export const RETRY_BACKOFF_BASE_MS_DEFAULT = 200;
export const RETRY_BACKOFF_MAX_MS_DEFAULT = 5000;
export const RETRY_BACKOFF_LIMIT_MS = 60000;
export const RETRY_MULTIPLIER_DEFAULT = 2;
export const RETRY_MULTIPLIER_LIMIT = 10;
export const RETRY_MODE_ALLOWLIST = "allowlist";
export const RETRY_MODE_DENYLIST = "denylist";
/** 默认重试状态集：5xx 与 429 */
export const RETRY_ON_DEFAULT = ["500", "502", "503", "504", "429"];

export function defaultRetryPolicy(): RetryPolicy {
  return {
    enabled: true,
    maxAttempts: RETRY_MAX_ATTEMPTS_DEFAULT,
    backoffBaseMs: RETRY_BACKOFF_BASE_MS_DEFAULT,
    backoffMaxMs: RETRY_BACKOFF_MAX_MS_DEFAULT,
    multiplier: RETRY_MULTIPLIER_DEFAULT,
    jitter: true,
    respectRetryAfter: true,
    retryOnMode: RETRY_MODE_ALLOWLIST,
    retryOn: [...RETRY_ON_DEFAULT]
  };
}

/**
 * 规范化：非法或缺失字段一律退回默认，保证表单与提交载荷都是有效值。
 */
export function normalizeRetryPolicy(raw: unknown): RetryPolicy {
  const empty = defaultRetryPolicy();
  if (!raw || typeof raw !== "object") {
    return empty;
  }
  const source = raw as Dict;
  const mode = String(source.retryOnMode ?? "").trim().toLowerCase();
  const statuses = Array.isArray(source.retryOn)
    ? source.retryOn.map(item => String(item ?? "").trim()).filter(item => /^\d+$/.test(item))
    : [];
  return {
    enabled: source.enabled !== false,
    maxAttempts: clampInt(source.maxAttempts, RETRY_MAX_ATTEMPTS_DEFAULT, 1, RETRY_MAX_ATTEMPTS_LIMIT),
    backoffBaseMs: clampInt(source.backoffBaseMs, RETRY_BACKOFF_BASE_MS_DEFAULT, 0, RETRY_BACKOFF_LIMIT_MS),
    backoffMaxMs: clampInt(source.backoffMaxMs, RETRY_BACKOFF_MAX_MS_DEFAULT, 0, RETRY_BACKOFF_LIMIT_MS),
    multiplier: clampNumber(source.multiplier, RETRY_MULTIPLIER_DEFAULT, 1, RETRY_MULTIPLIER_LIMIT),
    jitter: source.jitter !== false,
    respectRetryAfter: source.respectRetryAfter !== false,
    retryOnMode: mode === RETRY_MODE_DENYLIST ? RETRY_MODE_DENYLIST : RETRY_MODE_ALLOWLIST,
    retryOn: statuses.length ? statuses : [...RETRY_ON_DEFAULT]
  };
}

function clampInt(raw: unknown, fallback: number, min: number, max: number): number {
  const value = Number(raw);
  if (!Number.isFinite(value)) {
    return fallback;
  }
  return Math.min(max, Math.max(min, Math.round(value)));
}

function clampNumber(raw: unknown, fallback: number, min: number, max: number): number {
  const value = Number(raw);
  if (!Number.isFinite(value)) {
    return fallback;
  }
  return Math.min(max, Math.max(min, value));
}
