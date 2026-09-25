import { computed, ref } from "vue";
import { listCommonEnums } from "../api/enums";

/**
 * 代码枚举统一数据源（GET /api/common/enums）
 *
 * 仅包含已从 DB 迁移到后端的枚举，当前有：
 *  - ModelTypeEnum        13 项模型类型（code -> 中文名）
 *  - ModelApiProtocolEnum 接口协议（code -> 协议名）
 * 其余枚举分类仍在 ks_enum_configs，走 useEnums（/api/system/enums），两套来源不要混用。
 *
 * 用法：
 *   const { optionsOf, labelOf } = useEnumOptions();
 *   optionsOf("ModelTypeEnum");        // [{ value: "CHAT", label: "对话" }, ...]
 *   labelOf("ModelTypeEnum", "CHAT");  // "对话"
 */
const TTL = 5 * 60 * 1000;

const enumMap = ref({});
const loading = ref(false);
let loadedAt = 0;
let inflight = null;

function toOptions(entry) {
  if (!entry) {
    return [];
  }
  return Object.entries(entry).map(([code, label]) => ({ value: code, label: label || code }));
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
   * @param {string[]} [includeCodes] 仅保留这些 code（保持后端顺序）
   */
  function optionsOf(enumName, includeCodes) {
    const list = toOptions(enumMap.value[enumName]);
    if (!includeCodes?.length) {
      return list;
    }
    const set = new Set(includeCodes);
    return list.filter((item) => set.has(item.value));
  }

  /**
   * code -> label
   * @param {string} enumName 枚举键
   * @param {string} code
   * @param {string} [fallback] 未命中时的兜底文案，缺省回显 code
   */
  function labelOf(enumName, code, fallback) {
    if (code === null || code === undefined || code === "") {
      return fallback ?? "-";
    }
    const entry = enumMap.value[enumName];
    const label = entry ? entry[code] : null;
    return label || fallback || code;
  }

  /** 是否已加载过 */
  const ready = computed(() => loadedAt > 0);

  return { enumMap, loading, loadEnums, invalidateEnums, optionsOf, labelOf, ready };
}
