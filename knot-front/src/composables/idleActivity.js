import { getStorageItem, removeStorageItem, setStorageItem } from "../utils/storage";

/** 鏃犲悗绔姹傝秴杩囪鏃堕暱鍒欒嚜鍔ㄩ€€鍑猴紙姣锛?*/
export const IDLE_TIMEOUT_MS = 30 * 60 * 1000;

const LAST_ACTIVITY_KEY = "knot_last_activity";

export function touchIdleActivity() {
  setStorageItem(LAST_ACTIVITY_KEY, String(Date.now()));
}

export function getLastIdleActivityAt() {
  const raw = getStorageItem(LAST_ACTIVITY_KEY);
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : 0;
}

export function clearIdleActivity() {
  removeStorageItem(LAST_ACTIVITY_KEY);
}

export function isIdleTimedOut(now = Date.now()) {
  const last = getLastIdleActivityAt();
  if (!last) return false;
  return now - last >= IDLE_TIMEOUT_MS;
}
