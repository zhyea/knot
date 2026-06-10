import { reactive, ref } from "vue";

/**
 * 通用分页列表 composable
 *
 * @param {(params: object) => Promise<{list: Array, total: number}>} apiFn
 * @param {object} [defaults]
 * @param {number} [defaults.pageSize=20]
 * @param {object} [defaults.extra]
 */
export function usePageList(apiFn, defaults = {}) {
  const rows = ref([]);
  const loading = ref(false);
  const total = ref(0);
  const pageNum = ref(1);
  const pageSize = ref(defaults.pageSize || 20);
  const extra = reactive(defaults.extra || {});

  async function load() {
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
        rows.value = result.list || [];
        total.value = result.total || 0;
      }
      return result;
    } finally {
      loading.value = false;
    }
  }

  async function onPageChange(page) {
    pageNum.value = page;
    return load();
  }

  async function onSizeChange(size) {
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
