import { ref, readonly } from "vue";
import type { Dict, Row } from "../types";
import { listEnumsByCategory, listEnumCategories } from "../api/enums";

/**
 * 枚举数据缓存 composable
 *
 * 用法：
 *   const { options, loadOptions } = useEnums("provider_type");
 *   // options.value → [{ itemCode: "OPENAI", itemLabel: "OpenAI" }, ...]
 */
const CACHE: Record<string, { data: unknown; ts: number }> = {};
const TTL = 5 * 60 * 1000; // 5 分钟缓存

function isExpired(entry: { data: unknown; ts: number } | undefined): boolean {
  return !entry || Date.now() - entry.ts > TTL;
}

/**
 * 按分类加载枚举选项（带缓存）
 * @param {string} category  如 provider_type、model_type
 * @param {boolean} enabledOnly 是否只返回启用项，默认 true
 */
export function useEnums(category: string, enabledOnly = true) {
  const options = ref<Row[]>([]);
  const loading = ref(false);

  async function loadOptions() {
    loading.value = true;
    try {
      const cached = CACHE[category];
      if (!isExpired(cached)) {
        options.value = filter(cached.data, enabledOnly);
        return;
      }
      const data = await listEnumsByCategory(category);
      CACHE[category] = { data, ts: Date.now() };
      options.value = filter(data, enabledOnly);
    } finally {
      loading.value = false;
    }
  }

  function filter(data: unknown, onlyEnabled: boolean): Row[] {
    const list: Row[] = Array.isArray(data) ? data : ((data as Dict)?.list || []);
    return onlyEnabled ? list.filter((i: Row) => i.isEnabled !== false) : list;
  }

  /** 使指定分类的缓存失效 */
  function invalidate() {
    delete CACHE[category];
  }

  return { options, loading, loadOptions, invalidate };
}

/**
 * 获取所有分类列表
 */
export function useEnumCategories() {
  const categories = ref<Row[]>([]);
  const loading = ref(false);

  async function loadCategories() {
    loading.value = true;
    try {
      const data = await listEnumCategories();
      categories.value = Array.isArray(data) ? data : [];
    } finally {
      loading.value = false;
    }
  }

  return { categories, loading, loadCategories };
}

/** 清除全部枚举缓存 */
export function clearEnumCache() {
  Object.keys(CACHE).forEach((k) => delete CACHE[k]);
}

/**
 * 根据 itemCode 解析展示文案
 * @param {Array} options useEnums 返回的 options.value
 * @param {string} code
 * @param {string} [fallback]
 */
export function resolveEnumLabel(options: Row[], code: unknown, fallback = "—"): string {
  if (!code) return fallback;
  const list = Array.isArray(options) ? options : [];
  const item = list.find((i) => i.itemCode === code);
  return String(item?.itemLabel ?? code);
}
