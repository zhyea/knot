import {ElMessage} from "element-plus";
import router from "../router";
import {useAuth} from "./useAuth";
import {translate} from "./useLocale";
import {
  clearIdleActivity,
  IDLE_TIMEOUT_MS,
  isIdleTimedOut,
  startIdleActivityTracking,
  stopIdleActivityTracking,
  touchIdleActivity
} from "./idleActivity";

const CHECK_INTERVAL_MS = 60 * 1000;

let checkTimer: ReturnType<typeof setInterval> | null = null;
let autoLoggingOut = false;

export function startIdleSessionWatch() {
  startIdleActivityTracking();
  if (checkTimer) return;
  checkTimer = setInterval(checkIdleSession, CHECK_INTERVAL_MS);
}

export function stopIdleSessionWatch() {
  stopIdleActivityTracking();
  if (checkTimer) {
    clearInterval(checkTimer);
    checkTimer = null;
  }
}

/**
 * 应用启动时调用。
 *
 * 已登录即视为一次活跃：刷新页面、重新打开浏览器本身都是使用行为，
 * 不能沿用上次关闭时残留的时间戳，否则加载完不到一分钟就会被判定空闲退出。
 */
export function initIdleSession() {
  const { token } = useAuth();
  if (token.value) {
    touchIdleActivity();
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
    const minutes = Math.round(IDLE_TIMEOUT_MS / 60000);
    // 登出请求失败也必须把用户送回登录页：useAuth.logout 已在 finally 里清理本地登录态，
    // 这里再兜一层，避免请求异常导致既不跳转也不提示（静默无反馈）。
    await logout().catch(() => undefined);
    router.push("/login");
    ElMessage.warning(translate("session.idleLogout", {minutes}));
  } finally {
    autoLoggingOut = false;
    clearIdleActivity();
  }
}
