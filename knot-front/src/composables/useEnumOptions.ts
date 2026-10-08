import {computed, ref} from "vue";
import {listCommonEnums} from "@/api/enums";
import type {Dict, SelectOption} from "@/types";

/**
 * 代码枚举统一数据源（GET /api/common/enums）
 *
 * 响应结构为 **数组**：`{ 枚举键: [{code, label}, ...] }`。
 * 不再是 `code -> label` 的 map —— JSON object key 必然是字符串，用 map 会让数字 code
 * 退化成字符串 "1"，与字符串 code 无法区分（`{code: 0}` 会变成 `{"0": "禁用"}`）。
 *
 * 仅包含已从 DB 迁移到后端的枚举，当前有：
 *  - ModelTypeEnum        13 项模型类型（code -> 中文名）
 *  - ModelApiProtocolEnum 接口协议（code -> 协议名）
 *  - BillingModeEnum      计费模式（8 项，code -> 中文名）
 *  - PricingPlanEnum      进阶定价方案（FIXED/TIERED/PEAK_OFF_PEAK；可用性由 mode-capabilities 约束）
 *  - EnabledStatusEnum    二态可用状态（**数字 code**：1 启用 / 0 禁用）
 *  - RouteTargetTypeEnum  路由目标类型（模型/模型池）
 * 其余枚举分类仍在 ks_enum_configs，走 useEnums（/api/system/enums），两套来源不要混用。
 *
 * 用法：
 *   const { optionsOf, labelOf } = useEnumOptions();
 *   optionsOf("ModelTypeEnum");        // [{ value: "CHAT", label: "对话" }, ...]
 *   labelOf("ModelTypeEnum", "CHAT");  // "对话"
 *   labelOf("EnabledStatusEnum", 0);   // "禁用"  ← 0 是合法值，必须显式传数字
 *
 * ⚠ 数字 code 用**严格比较**：`0` 命中、`"0"` 不命中。任何地方都不要把 code 转成字符串再查。
 */
const TTL = 5 * 60 * 1000;

const enumMap = ref<Dict>({});
const loading = ref(false);
let loadedAt = 0;
let inflight: Promise<Dict> | null = null;

function toOptions(entry: unknown): SelectOption[] {
  if (!Array.isArray(entry)) {
    return [];
  }
  return entry.map((item) => ({
    value: item?.code,
    label: String(item?.label ?? item?.code ?? "")
  }));
}

/**
 * 加载全部代码枚举；全局只缓存一份，并发调用共用一个请求
 * @param {boolean} force 是否忽略缓存强制刷新
 */
async function loadEnums(force = false) {
  if (!force && loadedAt && Date.now() - loadedAt <= TTL) {
    return enumMap.value;
  }
  if (inflight) {
    return await inflight;
  }
  loading.value = true;
  inflight = listCommonEnums()
    .then((data) => {
      enumMap.value = data && typeof data === "object" ? data : {};
      loadedAt = Date.now();
      return enumMap.value;
    })
    .catch(() => {
      // 加载失败保留上一次成功的数据，避免界面闪空
      return enumMap.value;
    })
    .finally(() => {
      loading.value = false;
      inflight = null;
    });
  return await inflight;
}

/** 使缓存失效 */
function invalidateEnums() {
  loadedAt = 0;
  enumMap.value = {};
}

export function useEnumOptions() {
  // 共享缓存：任一组件首次使用时加载一次即可，后续调用直接命中缓存
  loadEnums();

  /**
   * 取某个枚举的下拉选项
   * @param {string} enumName 枚举键
   * @param {(string|number)[]} [includeCodes] 仅保留这些 code（保持后端顺序）
   */
  function optionsOf(enumName: string, includeCodes?: (string | number)[]): SelectOption[] {
    const list = toOptions(enumMap.value[enumName]);
    if (!includeCodes?.length) {
      return list;
    }
    const set = new Set<unknown>(includeCodes);
    return list.filter((item) => set.has(item.value));
  }

  /**
   * code -> label
   *
   * 严格比较：数字 `0` 只命中数字 code 0，不命中字符串 "0"。
   * @param {string} enumName 枚举键
   * @param {unknown} code
   * @param {string} [fallback] 未命中时的兜底文案，缺省回显 code
   */
  function labelOf(enumName: string, code: unknown, fallback?: string): string {
    if (code === null || code === undefined || code === "") {
      return fallback ?? "-";
    }
    const hit = toOptions(enumMap.value[enumName]).find((item) => item.value === code);
    if (hit) {
      return hit.label;
    }
    return fallback || String(code);
  }

  /** 是否已加载过 */
  const ready = computed(() => loadedAt > 0);

  return { enumMap, loading, loadEnums, invalidateEnums, optionsOf, labelOf, ready };
}
