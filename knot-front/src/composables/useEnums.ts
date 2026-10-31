import {ref} from "vue";
import type {Dict, Row} from "@/types";
import {listEnumItemsByCategory} from "@/api/enums";

/**
 * 枚举数据缓存 composable
 *
 * 用法：
 *   const { options, error, loadOptions } = useEnums("model_family");
 *   // options.value → [{ itemCode: "gpt", itemLabel: "GPT（OpenAI）" }, ...]
 *
 * <p>⚠⚠ {@code loadOptions} <b>永不 reject</b>：失败只写 {@code error}，不向上抛。
 * 全站调用点都是 {@code onMounted(() => loadOptions(true))} 这种不接 Promise 的写法，
 * 抛出去就是 unhandled rejection（曾一次性冒出 43 个）。需要提示的宿主读 {@code error} 自行处理。</p>
 */
const CACHE: Record<string, { data: unknown; ts: number }> = {};
const TTL = 5 * 60 * 1000; // 5 分钟缓存

function isExpired(entry: { data: unknown; ts: number } | undefined): boolean {
  return !entry || Date.now() - entry.ts > TTL;
}

/**
 * 按分类加载枚举选项（带缓存）
 * @param {string} category  如 model_family、discount_type（仅限仍由 DB 维护的分类）
 * @param {boolean} enabledOnly 是否只返回启用项，默认 true
 */
export function useEnums(category: string, enabledOnly = true) {
  const options = ref<Row[]>([]);
  const loading = ref(false);
  /** 最近一次加载的失败原因；成功即清空。调用方据此决定要不要提示，默认不弹 */
  const error = ref<unknown>(null);

  /**
   * 加载枚举选项
   * @param {boolean} force 是否忽略缓存强制回源。表单下拉/抽屉每次打开都应传 true，
   *   否则会命中模块级缓存拿到编辑前的旧值（DB 压力由后端 Caffeine 吸收）。
   */
  async function loadOptions(force: boolean = false) {
    loading.value = true;
    error.value = null;
    try {
      const cached = CACHE[category];
      if (!force && !isExpired(cached)) {
        options.value = filter(cached.data, enabledOnly);
        return;
      }
      const data = await listEnumItemsByCategory(category);
      CACHE[category] = {data, ts: Date.now()};
      options.value = filter(data, enabledOnly);
    } catch (e) {
      // 枚举下拉是「输入辅助」，加载失败不该把异常甩给调用方：全站调用点都是
      // `onMounted(() => loadOptions(true))` 这种不接 Promise 的写法，抛出去就是
      // unhandled rejection（曾一次性冒出 43 个）。失败只记录，由调用方按需提示。
      error.value = e;
      // 刻意不清空 options：force 重载失败时保留上一次成功的选项，
      // 让用户至少还能选，比突然变空更可用。
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

  return {options, loading, error, loadOptions, invalidate};
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
