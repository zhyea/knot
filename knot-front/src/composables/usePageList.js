import { ref, reactive } from "vue";

/**
 * 通用分页列表 composable
 *
 * @param {(params: object) => Promise<{list: Array, total: number, pageNum: number, pageSize: number}>} apiFn
 *   接收 { pageNum, pageSize, ...extra } 返回 PageResult 的 API 函数
 * @param {object} [defaults]
 * @param {number} [defaults.pageSize=20]  默认每页条数
 * @param {object} [defaults.extra]        额外查询参数（reactive，修改后调用 load 即可生效）
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
      const res = await apiFn({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...extra
      });
      // 兼容 PageResult 和普通数组两种返回
      if (Array.isArray(res)) {
        rows.value = res;
        total.value = res.length;
      } else {
        rows.value = res.list || [];
        total.value = res.total || 0;
      }
    } finally {
      loading.value = false;
    }
  }

  function onPageChange(page) {
    pageNum.value = page;
    load();
  }

  function onSizeChange(size) {
    pageSize.value = size;
    pageNum.value = 1;
    load();
  }

  /** 重置到第一页并刷新 */
  function resetPage() {
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
