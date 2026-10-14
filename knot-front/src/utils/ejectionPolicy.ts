/**
 * 异常摘除策略：默认值与规范化（Mirror 后端 EjectionPolicy / org.chobit.knot.gateway.model.EjectionPolicy）
 *
 * 摘除与重试正交：重试在同一目标内部原地重投，摘除作用于目标之间——窗口内反复异常
 * （上游故障类失败 / 首 token 过慢）的供应商模型在后续请求里被移出候选链，冷却结束后半开恢复。
 * 字段口径与后端紧凑构造器的归一化保持一致——缺省填充、越界收敛、模式归一。
 *
 * 配置分两种模式：
 *  - simple（默认）：仅暴露「窗口 / 阈值 / 慢阈值 / 冷却」；
 *  - professional：计数开关、冷却增长倍数与上限、探活租约、全摘 fail-open 全部可调。
 */

import type {Dict} from "@/types";

export interface EjectionPolicy {
  /** 是否启用摘除 */
  enabled: boolean;
  /** 异常计数窗口（秒） */
  windowSec: number;
  /** 窗口内异常次数阈值（失败 + 慢） */
  threshold: number;
  /** 是否统计「首 token 过慢」 */
  countSlow: boolean;
  /** 首 token 超时阈值（毫秒） */
  slowFirstTokenMs: number;
  /** 首次摘除的冷却时长（毫秒） */
  cooldownMs: number;
  /** 是否统计失败类异常 */
  countFailures: boolean;
  /** 探活失败后的冷却增长倍数 */
  cooldownMultiplier: number;
  /** 冷却时长上限（毫秒） */
  maxCooldownMs: number;
  /** 半开探活请求的租约时长（毫秒） */
  probeTimeoutMs: number;
  /** 候选被全部摘除时是否放行原候选链 */
  failOpenWhenAllEjected: boolean;
  /** 配置模式：simple 只暴露主参数 / professional 全字段 */
  mode: string;
}

/** 默认关闭：摘除会主动减少可用容量，误判代价高于漏判 */
export const EJECTION_WINDOW_SEC_DEFAULT = 60;
export const EJECTION_WINDOW_SEC_LIMIT = 3600;
export const EJECTION_THRESHOLD_DEFAULT = 5;
export const EJECTION_THRESHOLD_LIMIT = 1000;
export const EJECTION_SLOW_FIRST_TOKEN_MS_DEFAULT = 15000;
export const EJECTION_SLOW_FIRST_TOKEN_MS_LIMIT = 600000;
export const EJECTION_COOLDOWN_MS_DEFAULT = 60000;
export const EJECTION_COOLDOWN_MS_MIN = 1000;
export const EJECTION_COOLDOWN_MS_LIMIT = 3600000;
export const EJECTION_COOLDOWN_MULTIPLIER_DEFAULT = 2.0;
export const EJECTION_COOLDOWN_MULTIPLIER_LIMIT = 10;
export const EJECTION_MAX_COOLDOWN_MS_DEFAULT = 600000;
export const EJECTION_PROBE_TIMEOUT_MS_DEFAULT = 30000;
export const EJECTION_PROBE_TIMEOUT_MS_LIMIT = 300000;
export const EJECTION_MODE_SIMPLE = "simple";
export const EJECTION_MODE_PROFESSIONAL = "professional";

export function defaultEjectionPolicy(): EjectionPolicy {
  return {
    enabled: false,
    windowSec: EJECTION_WINDOW_SEC_DEFAULT,
    threshold: EJECTION_THRESHOLD_DEFAULT,
    countSlow: true,
    slowFirstTokenMs: EJECTION_SLOW_FIRST_TOKEN_MS_DEFAULT,
    cooldownMs: EJECTION_COOLDOWN_MS_DEFAULT,
    countFailures: true,
    cooldownMultiplier: EJECTION_COOLDOWN_MULTIPLIER_DEFAULT,
    maxCooldownMs: EJECTION_MAX_COOLDOWN_MS_DEFAULT,
    probeTimeoutMs: EJECTION_PROBE_TIMEOUT_MS_DEFAULT,
    failOpenWhenAllEjected: true,
    mode: EJECTION_MODE_SIMPLE
  };
}

/**
 * 规范化：非法或缺失字段一律退回默认，保证表单与提交载荷都是有效值。
 *
 * simple 模式下 professional 专属字段强制走内置默认，与后端 compact constructor 语义对齐。
 */
export function normalizeEjectionPolicy(raw: unknown): EjectionPolicy {
  const empty = defaultEjectionPolicy();
  if (!raw || typeof raw !== "object") {
    return empty;
  }
  const source = raw as Dict;
  const mode = normalizeConfigMode(source.mode);
  const enabled = source.enabled === true;
  const windowSec = clampInt(source.windowSec, EJECTION_WINDOW_SEC_DEFAULT, 1, EJECTION_WINDOW_SEC_LIMIT);
  const threshold = clampInt(source.threshold, EJECTION_THRESHOLD_DEFAULT, 1, EJECTION_THRESHOLD_LIMIT);
  const slowFirstTokenMs = clampInt(source.slowFirstTokenMs, EJECTION_SLOW_FIRST_TOKEN_MS_DEFAULT,
    1, EJECTION_SLOW_FIRST_TOKEN_MS_LIMIT);
  const cooldownMs = clampInt(source.cooldownMs, EJECTION_COOLDOWN_MS_DEFAULT,
    EJECTION_COOLDOWN_MS_MIN, EJECTION_COOLDOWN_MS_LIMIT);

  if (mode === EJECTION_MODE_SIMPLE) {
    return {
      enabled,
      windowSec,
      threshold,
      countSlow: true,
      slowFirstTokenMs,
      cooldownMs,
      countFailures: true,
      cooldownMultiplier: EJECTION_COOLDOWN_MULTIPLIER_DEFAULT,
      maxCooldownMs: EJECTION_MAX_COOLDOWN_MS_DEFAULT,
      probeTimeoutMs: EJECTION_PROBE_TIMEOUT_MS_DEFAULT,
      failOpenWhenAllEjected: true,
      mode
    };
  }

  const maxCooldownMs = clampInt(source.maxCooldownMs, EJECTION_MAX_COOLDOWN_MS_DEFAULT,
    cooldownMs, EJECTION_COOLDOWN_MS_LIMIT);
  return {
    enabled,
    windowSec,
    threshold,
    countSlow: source.countSlow !== false,
    slowFirstTokenMs,
    cooldownMs,
    countFailures: source.countFailures !== false,
    cooldownMultiplier: clampNumber(source.cooldownMultiplier, EJECTION_COOLDOWN_MULTIPLIER_DEFAULT,
      1, EJECTION_COOLDOWN_MULTIPLIER_LIMIT),
    maxCooldownMs,
    probeTimeoutMs: clampInt(source.probeTimeoutMs, EJECTION_PROBE_TIMEOUT_MS_DEFAULT,
      EJECTION_COOLDOWN_MS_MIN, EJECTION_PROBE_TIMEOUT_MS_LIMIT),
    failOpenWhenAllEjected: source.failOpenWhenAllEjected !== false,
    mode
  };
}

function normalizeConfigMode(raw: unknown): string {
  const value = String(raw ?? "").trim().toLowerCase();
  return value === EJECTION_MODE_PROFESSIONAL ? EJECTION_MODE_PROFESSIONAL : EJECTION_MODE_SIMPLE;
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
