import {onScopeDispose, reactive, ref, watch} from "vue";
import {useRoute, useRouter} from "vue-router";
import type {Dict, ListResponse, QueryFn} from "@/types";

export interface UseListQueryOptions {
  /** 列表接口 */
  apiFn: QueryFn;
  /** 筛选字段默认值（字符串 / 字符串数组 / null） */
  fields?: Dict;
  /** 每页条数默认值 */
  pageSize?: number;
  /** 是否把筛选条件写入地址栏 */
  syncUrl?: boolean;
  /** 输入防抖（ms） */
  debounce?: number;
}

/**
 * 列表页统一查询 composable
 *
 * 相对旧的 `usePageList + useAutoQuery` 组合，额外提供：
 * 1. 参数清洗：字符串 trim，空串 / 空数组 / null 一律不下发；
 * 2. URL 同步：筛选条件与分页写回地址栏，刷新、前进后退、分享链接都能还原；
 * 3. 统一入口：`handleQuery`（立即查）/ `handleReset`（恢复默认值并清掉 URL 上的筛选参数）。
 */
export function useListQuery(options: UseListQueryOptions) {
  const {
    apiFn,
    fields = {},
    pageSize: defaultPageSize = 20,
    syncUrl = true,
    debounce = 300
  } = options;

  const route = useRoute();
  const router = useRouter();

  const rows = ref<any[]>([]);
  const loading = ref(false);
  const total = ref(0);
  const pageNum = ref(1);
  const pageSize = ref(defaultPageSize);
  const query = reactive<Dict>(cloneFields(fields));

  const fieldKeys = Object.keys(fields);

  restoreFromUrl();

  function cloneFields(source: Dict): Dict {
    return JSON.parse(JSON.stringify(source));
  }

  /** 单值规范化：trim 后为空一律视为「未填写」 */
  function normalize(value: unknown): string | string[] | undefined {
    if (Array.isArray(value)) {
      const list = value.map((item) => String(item).trim()).filter(Boolean);
      return list.length > 0 ? list : undefined;
    }
    if (value === null || value === undefined) {
      return undefined;
    }
    const text = String(value).trim();
    return text === "" ? undefined : text;
  }

  function restoreFromUrl() {
    if (!syncUrl) {
      return;
    }
    const source = route.query;
    const page = Number(source.page);
    const size = Number(source.size);
    if (Number.isInteger(page) && page > 0) {
      pageNum.value = page;
    }
    if (Number.isInteger(size) && size > 0) {
      pageSize.value = size;
    }
    fieldKeys.forEach((key) => {
      const raw = source[key];
      if (raw === undefined || raw === null) {
        return;
      }
      query[key] = Array.isArray(fields[key])
        ? String(raw)
            .split(",")
            .map((item) => item.trim())
            .filter(Boolean)
        : String(raw);
    });
  }

  function buildParams(): Dict {
    const params: Dict = { pageNum: pageNum.value, pageSize: pageSize.value };
    fieldKeys.forEach((key) => {
      const value = normalize(query[key]);
      if (value !== undefined) {
        params[key] = value;
      }
    });
    return params;
  }

  function syncUrlQuery() {
    if (!syncUrl) {
      return;
    }
    const next: Dict = { ...route.query };
    fieldKeys.forEach((key) => {
      const value = normalize(query[key]);
      if (value === undefined) {
        delete next[key];
      } else {
        next[key] = Array.isArray(value) ? value.join(",") : value;
      }
    });
    if (pageNum.value > 1) {
      next.page = String(pageNum.value);
    } else {
      delete next.page;
    }
    if (pageSize.value !== defaultPageSize) {
      next.size = String(pageSize.value);
    } else {
      delete next.size;
    }
    router.replace({ query: next });
  }

  async function load(): Promise<ListResponse> {
    loading.value = true;
    try {
      const result = await apiFn(buildParams());
      if (Array.isArray(result)) {
        rows.value = result;
        total.value = result.length;
      } else {
        rows.value = result?.list || [];
        total.value = result?.total || 0;
      }
      return result;
    } finally {
      loading.value = false;
    }
  }

  async function onPageChange(page: number) {
    pageNum.value = page;
    syncUrlQuery();
    return load();
  }

  async function onSizeChange(size: number) {
    pageSize.value = size;
    pageNum.value = 1;
    syncUrlQuery();
    return load();
  }

  async function resetPage() {
    pageNum.value = 1;
    syncUrlQuery();
    return load();
  }

  let timer: ReturnType<typeof setTimeout> | null = null;
  let suspended = false;

  // 分页变化统一同步到地址栏，页面内任何改分页的入口都会走到这里
  watch([pageNum, pageSize], syncUrlQuery);

  function clearTimer() {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  }

  watch(
    query,
    () => {
      if (suspended) {
        return;
      }
      clearTimer();
      timer = setTimeout(() => {
        timer = null;
        resetPage();
      }, debounce);
    },
    { deep: true, flush: "post" }
  );

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

  /** 点「查询」/ 回车：跳过防抖立即执行，并把页码重置到第一页 */
  function handleQuery() {
    return pauseAutoQuery(() => resetPage());
  }

  /** 点「重置」：恢复默认筛选值，同时清掉地址栏上的筛选与分页参数 */
  function handleReset() {
    return pauseAutoQuery(() => {
      Object.assign(query, cloneFields(fields));
      return resetPage();
    });
  }

  onScopeDispose(clearTimer);

  return {
    query,
    rows,
    loading,
    total,
    pageNum,
    pageSize,
    load,
    onPageChange,
    onSizeChange,
    resetPage,
    handleQuery,
    handleReset,
    pauseAutoQuery
  };
}
