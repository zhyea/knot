import type {Dict} from "@/types";
import {UNIT_PRICE_PRECISION} from "@/utils/billingPrice";

/**
 * Compatible with Jackson LocalDateTime arrays like `[y,m,d,h,mi,s,nano]`.
 */
export function formatDateTime(value: unknown): string {
  if (value == null || value === "") return "—";
  if (Array.isArray(value)) {
    const [y, m = 1, d = 1, h = 0, mi = 0, s = 0] = value;
    const pad = (n: unknown) => String(n).padStart(2, "0");
    return `${y}-${pad(m)}-${pad(d)} ${pad(h)}:${pad(mi)}:${pad(s)}`;
  }
  if (typeof value === "string" && value.includes("T")) {
    return value.replace("T", " ").slice(0, 19);
  }
  return String(value);
}

/**
 * 千分位分隔的整数展示（上下文长度、模型 ID 序号等大数值列）。
 * 非数值 / 空值返回 fallback，避免把 "-" 显示成 "NaN"。
 */
export function formatThousands(value: unknown, fallback = "—"): string {
  if (value == null || value === "") return fallback;
  const n = Number(value);
  if (!Number.isFinite(n)) return String(value);
  return Math.trunc(n).toLocaleString("en-US");
}

export function formatJson(obj: unknown): string {
  if (obj == null) return "";
  try {
    return JSON.stringify(obj, null, 2);
  } catch {
    return String(obj);
  }
}

export function stringifyJson(value: unknown, space: string | number = 0, fallback = ""): string {
  if (value == null) return fallback;
  try {
    return JSON.stringify(value, null, space);
  } catch {
    return String(value);
  }
}

export function parseJson(text: unknown, fallback: unknown = null): unknown {
  if (text == null || String(text).trim() === "") return fallback;
  try {
    return JSON.parse(String(text));
  } catch {
    return fallback;
  }
}

export function parseJsonResult(
  text: unknown,
  fallback: unknown = null
): { value: unknown; error: unknown } {
  if (text == null || String(text).trim() === "") {
    return { value: fallback, error: null };
  }
  try {
    return { value: JSON.parse(String(text)), error: null };
  } catch (error) {
    return { value: fallback, error };
  }
}

export function parseJsonObject<T = Dict>(text: unknown, fallback: T = {} as T): T {
  const parsed = parseJson(text, fallback);
  return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? (parsed as T) : fallback;
}

export function isValidJsonText(text: unknown): boolean {
  if (text == null || String(text).trim() === "") return true;
  const invalid = Symbol("invalid_json");
  return parseJson(text, invalid) !== invalid;
}

export function formatJsonText(
  value: unknown,
  indent: string | number = "\t",
  fallback = ""
): string {
  if (value == null || value === "") return fallback;
  try {
    const parsed = typeof value === "string" ? JSON.parse(value) : value;
    return JSON.stringify(parsed, null, indent);
  } catch {
    return String(value);
  }
}

export function formatJsonArray(
  value: unknown,
  fallback = "—",
  separator = " / "
): string {
  if (value == null || value === "") return fallback;
  try {
    const parsed = typeof value === "string" ? JSON.parse(value) : value;
    return Array.isArray(parsed) && parsed.length ? parsed.join(separator) : fallback;
  } catch {
    return String(value);
  }
}

export function escapeHtml(value: unknown): string {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

export function highlightJsonHtml(value: unknown): string {
  return escapeHtml(value).replace(
    /("(?:\\u[\da-fA-F]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(?:true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g,
    (match) => {
      let cls = "json-number";
      if (match.startsWith('"')) {
        cls = match.endsWith(":") ? "json-key" : "json-string";
      } else if (match === "true" || match === "false") {
        cls = "json-boolean";
      } else if (match === "null") {
        cls = "json-null";
      }
      return `<span class="${cls}">${match}</span>`;
    }
  );
}

/**
 * Format money with 8 decimal places.
 *
 * <p>精度与计费侧 {@code setScale(8, HALF_UP)} 一致：成本单价常在 1e-6 ~ 1e-8 量级，
 * 4 位会把 0.00000001 显示成 0.0000，看起来像「没花钱」。</p>
 */
export function fmtMoney(v: unknown): string {
  if (v == null || v === "") return "—";
  const n = Number(v);
  if (Number.isNaN(n)) return String(v);
  return n.toFixed(UNIT_PRICE_PRECISION);
}

/**
 * 计费项精度收敛：保留 8 位小数上限（输入框不做显示补零，仅在确认/提交时收敛）。
 * 空/非法值返回 undefined，便于直接回写给可空字段。
 *
 * <p>默认 8 位与 {@link UNIT_PRICE_PRECISION} 一致；用 6 位会把 1e-8 单价抹成 0。</p>
 */
export function roundPrice(value: unknown, decimals = UNIT_PRICE_PRECISION): number | undefined {
  if (value == null || value === "") return undefined;
  const n = Number(value);
  if (Number.isNaN(n)) return undefined;
  return Math.round(n * 10 ** decimals) / 10 ** decimals;
}

/**
 * Parse `rateLimitJson` and `quotaJson` into policy objects.
 */
export function parsePolicies(form: {
  rateLimitJson?: string;
  quotaJson?: string;
}): { rateLimitPolicy: unknown; quotaPolicy: unknown } {
  const r = parseJson((form.rateLimitJson || "").trim(), undefined);
  const q = parseJson((form.quotaJson || "").trim(), undefined);
  return {
    rateLimitPolicy: r === undefined ? null : r,
    quotaPolicy: q === undefined ? null : q
  };
}
