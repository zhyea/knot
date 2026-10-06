/**
 * 失败重试策略：默认值与规范化（Mirror 后端 RetryPolicy / org.chobit.knot.gateway.model.RetryPolicy）
 *
 * 重试位于「跨候选 failover」之前：同一路由目标连续失败先在原地重投，次数耗尽才切下一个候选。
 * 字段口径与后端紧凑构造器的归一化保持一致——缺省填充、越界收敛、状态集过滤非法项。
 *
 * 配置分两种模式：
 *  - simple（默认）：仅暴露「总尝试次数 + 判定方式 + 状态码」，其余字段退回内置默认；
 *  - professional：退避基数/上限/倍数/抖动/Retry-After 全部可调。
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
  /** 指数退避倍数（1 位小数浮点，缺省 1.0 表示恒定退避） */
  multiplier: number;
  /** 退避区间内随机取值，打散重试尖峰 */
  jitter: boolean;
  /** 服从上游 Retry-After（429/503） */
  respectRetryAfter: boolean;
  /** allowlist：命中才重试；denylist：命中才不重试 */
  retryOnMode: string;
  /** 参与判定的上游 HTTP 状态码 */
  retryOn: string[];
  /** 配置模式：simple 仅尝试次数+判定方式+状态码 / professional 全字段 */
  mode: string;
}

export const RETRY_MAX_ATTEMPTS_DEFAULT = 2;
export const RETRY_MAX_ATTEMPTS_LIMIT = 10;
export const RETRY_BACKOFF_BASE_MS_DEFAULT = 200;
export const RETRY_BACKOFF_MAX_MS_DEFAULT = 5000;
export const RETRY_BACKOFF_LIMIT_MS = 60000;
export const RETRY_MULTIPLIER_DEFAULT = 1.0;
export const RETRY_MULTIPLIER_LIMIT = 10;
export const RETRY_JITTER_DEFAULT = false;
export const RETRY_MODE_SIMPLE = "simple";
export const RETRY_MODE_PROFESSIONAL = "professional";
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
    jitter: RETRY_JITTER_DEFAULT,
    respectRetryAfter: true,
    retryOnMode: RETRY_MODE_ALLOWLIST,
    retryOn: [...RETRY_ON_DEFAULT],
    mode: RETRY_MODE_SIMPLE
  };
}

/**
 * 规范化：非法或缺失字段一律退回默认，保证表单与提交载荷都是有效值。
 *
 * <p>simple 模式下，除「总尝试次数 + 判定方式 + 状态码」外其余字段强制走内置默认，
 * 与后端 {@code normalizeConfigMode} 语义对齐；professional 模式才放开全部字段。</p>
 */
export function normalizeRetryPolicy(raw: unknown): RetryPolicy {
  const empty = defaultRetryPolicy();
  if (!raw || typeof raw !== "object") {
    return empty;
  }
  const source = raw as Dict;
  const mode = normalizeConfigMode(source.mode);
  const retryOnMode = normalizeRetryOnMode(source.retryOnMode);
  const statuses = Array.isArray(source.retryOn)
    ? source.retryOn.map(item => String(item ?? "").trim()).filter(item => /^\d+$/.test(item))
    : [];
  const retryOn = statuses.length ? statuses : [...RETRY_ON_DEFAULT];
  const maxAttempts = clampInt(source.maxAttempts, RETRY_MAX_ATTEMPTS_DEFAULT, 1, RETRY_MAX_ATTEMPTS_LIMIT);
  const enabled = source.enabled !== false;

  // 简单模式：只保留「尝试次数 + 判定方式 + 状态码」，其余字段退回内置默认
  if (mode === RETRY_MODE_SIMPLE) {
    return {
      enabled,
      maxAttempts,
      backoffBaseMs: RETRY_BACKOFF_BASE_MS_DEFAULT,
      backoffMaxMs: RETRY_BACKOFF_MAX_MS_DEFAULT,
      multiplier: RETRY_MULTIPLIER_DEFAULT,
      jitter: RETRY_JITTER_DEFAULT,
      respectRetryAfter: true,
      retryOnMode,
      retryOn,
      mode
    };
  }

  return {
    enabled,
    maxAttempts,
    backoffBaseMs: clampInt(source.backoffBaseMs, RETRY_BACKOFF_BASE_MS_DEFAULT, 0, RETRY_BACKOFF_LIMIT_MS),
    backoffMaxMs: clampInt(source.backoffMaxMs, RETRY_BACKOFF_MAX_MS_DEFAULT, 0, RETRY_BACKOFF_LIMIT_MS),
    multiplier: clampNumber(source.multiplier, RETRY_MULTIPLIER_DEFAULT, 1, RETRY_MULTIPLIER_LIMIT),
    jitter: source.jitter === true,
    respectRetryAfter: source.respectRetryAfter !== false,
    retryOnMode,
    retryOn,
    mode
  };
}

function normalizeConfigMode(raw: unknown): string {
  const value = String(raw ?? "").trim().toLowerCase();
  return value === RETRY_MODE_PROFESSIONAL ? RETRY_MODE_PROFESSIONAL : RETRY_MODE_SIMPLE;
}

function normalizeRetryOnMode(raw: unknown): string {
  const value = String(raw ?? "").trim().toLowerCase();
  return value === RETRY_MODE_DENYLIST ? RETRY_MODE_DENYLIST : RETRY_MODE_ALLOWLIST;
}

function clampInt(raw: unknown, fallback: number, min: number, max: number): number {
  const value = Number(raw);
  if (!Number.isFinite(value)) {
    return fallback;
  }
  return Math.min(max, Math.max(min, Math.round(value)));
}

/** 越界收敛 + 1 位小数截断，对齐后端 roundToOneDecimal */
function clampNumber(raw: unknown, fallback: number, min: number, max: number): number {
  const value = Number(raw);
  if (!Number.isFinite(value)) {
    return fallback;
  }
  const clamped = Math.min(max, Math.max(min, value));
  return Math.round(clamped * 10) / 10;
}
