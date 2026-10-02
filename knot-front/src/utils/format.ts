import type {Dict} from "@/types";

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
 * Format money with 4 decimal places.
 */
export function fmtMoney(v: unknown): string {
  if (v == null || v === "") return "—";
  const n = Number(v);
  if (Number.isNaN(n)) return String(v);
  return n.toFixed(4);
}

/**
 * 计费项精度收敛：保留 6 位小数上限（输入框不做显示补零，仅在确认/提交时收敛）。
 * 空/非法值返回 undefined，便于直接回写给可空字段。
 */
export function roundPrice(value: unknown, decimals = 6): number | undefined {
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
