/**
 * Compatible with Jackson LocalDateTime arrays like `[y,m,d,h,mi,s,nano]`.
 */
export function formatDateTime(value) {
  if (value == null || value === "") return "—";
  if (Array.isArray(value)) {
    const [y, m = 1, d = 1, h = 0, mi = 0, s = 0] = value;
    const pad = (n) => String(n).padStart(2, "0");
    return `${y}-${pad(m)}-${pad(d)} ${pad(h)}:${pad(mi)}:${pad(s)}`;
  }
  if (typeof value === "string" && value.includes("T")) {
    return value.replace("T", " ").slice(0, 19);
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

export function stringifyJson(value, space = 0, fallback = "") {
  if (value == null) return fallback;
  try {
    return JSON.stringify(value, null, space);
  } catch {
    return String(value);
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

export function parseJsonResult(text, fallback = null) {
  if (text == null || String(text).trim() === "") {
    return { value: fallback, error: null };
  }
  try {
    return { value: JSON.parse(text), error: null };
  } catch (error) {
    return { value: fallback, error };
  }
}

export function parseJsonObject(text, fallback = {}) {
  const parsed = parseJson(text, fallback);
  return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : fallback;
}

export function isValidJsonText(text) {
  if (text == null || String(text).trim() === "") return true;
  const invalid = Symbol("invalid_json");
  return parseJson(text, invalid) !== invalid;
}

export function formatJsonText(value, indent = "\t", fallback = "") {
  if (value == null || value === "") return fallback;
  try {
    const parsed = typeof value === "string" ? JSON.parse(value) : value;
    return JSON.stringify(parsed, null, indent);
  } catch {
    return String(value);
  }
}

export function formatJsonArray(value, fallback = "—", separator = " / ") {
  if (value == null || value === "") return fallback;
  try {
    const parsed = typeof value === "string" ? JSON.parse(value) : value;
    return Array.isArray(parsed) && parsed.length ? parsed.join(separator) : fallback;
  } catch {
    return String(value);
  }
}

export function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

export function highlightJsonHtml(value) {
  return escapeHtml(value).replace(
    /("(?:\\u[\da-fA-F]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(?:true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g,
    (match) => {
      let cls = "json-number";
      if (match.startsWith("\"")) {
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
export function fmtMoney(v) {
  if (v == null || v === "") return "—";
  const n = Number(v);
  if (Number.isNaN(n)) return String(v);
  return n.toFixed(4);
}

/**
 * Parse `rateLimitJson` and `quotaJson` into policy objects.
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
