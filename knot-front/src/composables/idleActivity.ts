import {getStorageItem, removeStorageItem, setStorageItem} from "@/utils/storage";

// 连续无操作达到该时长后自动退出登录，单位：毫秒
export const IDLE_TIMEOUT_MS = 30 * 60 * 1000;

/**
 * localStorage 落盘节流间隔。
 * mousemove 这类高频事件不能每次都写 localStorage（同源写入是同步的，会阻塞主线程），
 * 因此内存里始终保留最新活跃时刻，落盘按最小间隔摊开。
 */
const PERSIST_THROTTLE_MS = 10 * 1000;

/** mousemove 事件自身的节流间隔，避免鼠标静止前的每个像素移动都进入处理函数 */
const MOVE_EVENT_THROTTLE_MS = 1000;

const LAST_ACTIVITY_KEY = "knot_last_activity";

/** 监听期间不重复注册 */
let tracking = false;

/** 进程内最新活跃时刻；落盘有节流，空闲判定必须以它为准 */
let lastActivityAt = 0;

/** 最近一次写入 localStorage 的时刻，用于落盘节流 */
let lastPersistedAt = 0;

/** 最近一次处理 mousemove 的时刻，用于事件级节流 */
let lastMoveHandledAt = 0;

function readPersistedActivityAt(): number {
  const value = Number(getStorageItem(LAST_ACTIVITY_KEY));
  return Number.isFinite(value) && value > 0 ? value : 0;
}

export function touchIdleActivity() {
  const now = Date.now();
  lastActivityAt = now;
  if (now - lastPersistedAt < PERSIST_THROTTLE_MS) {
    return;
  }
  lastPersistedAt = now;
  setStorageItem(LAST_ACTIVITY_KEY, String(now));
}

/**
 * 取最近一次活跃时刻。
 * 取内存值与 localStorage 的较大者：前者覆盖本标签页的节流窗口，
 * 后者让其它标签页写入的活跃时间对本标签页同样生效。
 */
export function getLastIdleActivityAt() {
  return Math.max(lastActivityAt, readPersistedActivityAt());
}

export function clearIdleActivity() {
  lastActivityAt = 0;
  lastPersistedAt = 0;
  lastMoveHandledAt = 0;
  removeStorageItem(LAST_ACTIVITY_KEY);
}

export function isIdleTimedOut(now = Date.now()) {
  const last = getLastIdleActivityAt();
  if (!last) {
    return false;
  }
  return now - last >= IDLE_TIMEOUT_MS;
}

function handleActivity() {
  touchIdleActivity();
}

function handleMouseMove() {
  const now = Date.now();
  if (now - lastMoveHandledAt < MOVE_EVENT_THROTTLE_MS) {
    return;
  }
  lastMoveHandledAt = now;
  touchIdleActivity();
}

/** 切回前台即视为活跃：笔记本合盖休眠后唤醒，不该因为计时器被节流而直接判定超时 */
function handleVisibilityChange() {
  if (document.visibilityState === "visible") {
    touchIdleActivity();
  }
}

// 捕获阶段监听，覆盖所有滚动容器与元素；passive 避免影响滚动与输入的默认行为
const ACTIVITY_EVENTS = ["pointerdown", "keydown", "wheel", "touchstart", "focus", "scroll"];

/**
 * 开始把真实用户交互计入活跃时间。
 *
 * 空闲超时的判定不能只依赖「发出了后端请求」：填表单、编辑配置 JSON、勾选授权菜单树、
 * 滚动列表、前端本地排序筛选等操作都不产生网络请求，却同样是持续使用。
 */
export function startIdleActivityTracking() {
  if (tracking) {
    return;
  }
  tracking = true;
  for (const event of ACTIVITY_EVENTS) {
    window.addEventListener(event, handleActivity, {capture: true, passive: true});
  }
  window.addEventListener("mousemove", handleMouseMove, {passive: true});
  document.addEventListener("visibilitychange", handleVisibilityChange);
}

export function stopIdleActivityTracking() {
  if (!tracking) {
    return;
  }
  tracking = false;
  for (const event of ACTIVITY_EVENTS) {
    window.removeEventListener(event, handleActivity, {capture: true});
  }
  window.removeEventListener("mousemove", handleMouseMove);
  document.removeEventListener("visibilitychange", handleVisibilityChange);
}
