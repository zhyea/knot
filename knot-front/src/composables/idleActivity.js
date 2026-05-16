/** 无后端请求超过该时长则自动退出（毫秒） */
export const IDLE_TIMEOUT_MS = 30 * 60 * 1000;

const LAST_ACTIVITY_KEY = "knot_last_activity";

export function touchIdleActivity() {
  localStorage.setItem(LAST_ACTIVITY_KEY, String(Date.now()));
}

export function getLastIdleActivityAt() {
  const raw = localStorage.getItem(LAST_ACTIVITY_KEY);
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : 0;
}

export function clearIdleActivity() {
  localStorage.removeItem(LAST_ACTIVITY_KEY);
}

export function isIdleTimedOut(now = Date.now()) {
  const last = getLastIdleActivityAt();
  if (!last) return false;
  return now - last >= IDLE_TIMEOUT_MS;
}
