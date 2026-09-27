import {reactive, ref, type Ref} from "vue";
import type {Dict, ListResponse, QueryFn} from "@/types";

export interface UsePageListDefaults {
  pageSize?: number;
  extra?: Dict;
}

/**
 * 通用分页列表 composable
 *
 * @param apiFn 列表接口，入参为 `pageNum / pageSize + extra`
 * @param defaults 初始分页大小与固定附加参数
 */
export function usePageList<T = any>(apiFn: QueryFn<T>, defaults: UsePageListDefaults = {}) {
  const rows = ref<T[]>([]) as Ref<T[]>;
  const loading = ref(false);
  const total = ref(0);
  const pageNum = ref(1);
  const pageSize = ref(defaults.pageSize || 20);
  const extra = reactive<Dict>(defaults.extra || {});

  async function load(): Promise<ListResponse<T>> {
    loading.value = true;
    try {
      const result = await apiFn({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...extra
      });
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
    return load();
  }

  async function onSizeChange(size: number) {
    pageSize.value = size;
    pageNum.value = 1;
    return load();
  }

  async function resetPage() {
    pageNum.value = 1;
    return load();
  }

  return {
    rows,
    loading,
    total,
    pageNum,
    pageSize,
    extra,
    load,
    onPageChange,
    onSizeChange,
    resetPage
  };
}
