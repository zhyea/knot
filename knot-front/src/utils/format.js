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
