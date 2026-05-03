/**
 * 兼容 Jackson 将 LocalDateTime 序列化为数组 `[y,m,d,h,mi,s,nano]`
 */
export function formatDateTime(value) {
  if (value == null || value === "") return "—";
  if (Array.isArray(value)) {
    const [y, m = 1, d = 1, h = 0, mi = 0, s = 0] = value;
    const pad = (n) => String(n).padStart(2, "0");
    return `${y}-${pad(m)}-${pad(d)} ${pad(h)}:${pad(mi)}:${pad(s)}`;
  }
  return String(value);
}

export function formatJson(obj) {
  if (obj == null) return "";
  try {
    return JSON.stringify(obj, null, 2);
  } catch {
    return String(obj);
  }
}

export function parseJson(text, fallback = null) {
  if (text == null || String(text).trim() === "") return fallback;
  try {
    return JSON.parse(text);
  } catch {
    return fallback;
  }
}

/**
 * 格式化金额，保留 4 位小数
 */
export function fmtMoney(v) {
  if (v == null || v === "") return "—";
  const n = Number(v);
  if (Number.isNaN(n)) return String(v);
  return n.toFixed(4);
}

/**
 * 从表单 rateLimitJson / quotaJson 字段解析为 rateLimitPolicy / quotaPolicy
 * @param {{ rateLimitJson?: string, quotaJson?: string }} form
 * @returns {{ rateLimitPolicy: object|null, quotaPolicy: object|null }}
 */
export function parsePolicies(form) {
  const r = parseJson((form.rateLimitJson || "").trim(), undefined);
  const q = parseJson((form.quotaJson || "").trim(), undefined);
  return {
    rateLimitPolicy: r === undefined ? null : r,
    quotaPolicy: q === undefined ? null : q
  };
}
