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
    flush: "post"
  });

  onScopeDispose(clearTimer);

  return {
    pauseAutoQuery
  };
}
