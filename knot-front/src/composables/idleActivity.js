import { getStorageItem, removeStorageItem, setStorageItem } from "../utils/storage";

// 无后端请求超过该时长则自动退出，单位：毫秒
export const IDLE_TIMEOUT_MS = 30 * 60 * 1000;

const LAST_ACTIVITY_KEY = "knot_last_activity";

export function touchIdleActivity() {
  setStorageItem(LAST_ACTIVITY_KEY, String(Date.now()));
}

export function getLastIdleActivityAt() {
  const raw = getStorageItem(LAST_ACTIVITY_KEY);
  const value = Number(raw);
  return Number.isFinite(value) && value > 0 ? value : 0;
}

export function clearIdleActivity() {
  removeStorageItem(LAST_ACTIVITY_KEY);
}

export function isIdleTimedOut(now = Date.now()) {
  const last = getLastIdleActivityAt();
  if (!last) {
    return false;
  }
  return now - last >= IDLE_TIMEOUT_MS;
}
