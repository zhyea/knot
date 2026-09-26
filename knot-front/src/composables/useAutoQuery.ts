import { isRef, onScopeDispose, watch, type WatchSource } from "vue";

export interface UseAutoQueryOptions {
  /** 输入防抖（ms） */
  debounce?: number;
  /** 是否深度监听 */
  deep?: boolean;
  /** 返回 false 时本次变更不触发查询 */
  enabled?: () => boolean;
}

/**
 * 监听 source，变更后防抖触发 query
 *
 * @param source ref / getter / 普通对象
 * @param query 变更回调
 */
export function useAutoQuery(
  source: WatchSource | Record<string, any>,
  query: () => void,
  options: UseAutoQueryOptions = {}
) {
  const debounce = options.debounce ?? 300;
  const deep = options.deep ?? true;
  const enabled = options.enabled ?? (() => true);

  let initialized = false;
  let suspended = false;
  let timer: ReturnType<typeof setTimeout> | null = null;

  const watchSource = (
    typeof source === "function" ? source : isRef(source) ? source : () => source
  ) as WatchSource;

  function clearTimer() {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  }

  function schedule() {
    if (!initialized) {
      initialized = true;
      return;
    }
    if (suspended || !enabled()) {
      return;
    }
    clearTimer();
    timer = setTimeout(() => {
      timer = null;
      query();
    }, debounce);
  }

  function pauseAutoQuery(fn?: () => unknown) {
    suspended = true;
    clearTimer();
    try {
      return fn?.();
    } finally {
      queueMicrotask(() => {
        suspended = false;
      });
    }
  }

  watch(watchSource, schedule, {
    deep,
    flush: "post",
    // 必须立即执行一次：watch 不带 immediate 时首次回调就是用户的第一次真实修改，
    // 会被 schedule() 的 initialized 守卫吞掉，导致“改了筛选条件列表不刷新”。
    // 带上 immediate 后，这一次消耗在初始化上，用户改动才真正触发查询。
    immediate: true
  });

  onScopeDispose(clearTimer);

  return {
    pauseAutoQuery
  };
}
