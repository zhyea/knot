import { ElMessage } from "element-plus";
import router from "../router";
import { useAuth } from "./useAuth";
import {
  clearIdleActivity,
  getLastIdleActivityAt,
  isIdleTimedOut,
  touchIdleActivity
} from "./idleActivity";

const CHECK_INTERVAL_MS = 60 * 1000;

let checkTimer = null;
let autoLoggingOut = false;

export function startIdleSessionWatch() {
  if (checkTimer) return;
  checkTimer = setInterval(checkIdleSession, CHECK_INTERVAL_MS);
}

export function stopIdleSessionWatch() {
  if (checkTimer) {
    clearInterval(checkTimer);
    checkTimer = null;
  }
}

/** 应用启动时调用：已登录则刷新活动时间并启动轮询 */
export function initIdleSession() {
  const { token } = useAuth();
  if (token.value) {
    if (!getLastIdleActivityAt()) {
      touchIdleActivity();
    }
  }
  startIdleSessionWatch();
}

async function checkIdleSession() {
  if (autoLoggingOut) return;

  const { token, logout } = useAuth();
  if (!token.value) return;

  if (!isIdleTimedOut()) return;

  autoLoggingOut = true;
  try {
    await logout();
    router.push("/login");
    ElMessage.warning("超过 30 分钟未操作，已自动退出登录");
  } finally {
    autoLoggingOut = false;
    clearIdleActivity();
  }
}
