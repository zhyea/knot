import { isRef, onScopeDispose, watch } from "vue";

export function useAutoQuery(source, query, options = {}) {
  const debounce = options.debounce ?? 300;
  const deep = options.deep ?? true;
  const enabled = options.enabled ?? (() => true);

  let initialized = false;
  let suspended = false;
  let timer = null;

  const watchSource =
    typeof source === "function" ? source : isRef(source) ? source : () => source;

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

  function pauseAutoQuery(fn) {
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
