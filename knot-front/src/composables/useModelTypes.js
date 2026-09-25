import { computed, ref } from "vue";
import { listModelTypes } from "../api/models";

/**
 * 模型类型「协议规则」数据源（GET /api/models/types）
 *
 * 只负责协议相关能力：
 *  - protocolsOf(code)  模型类型 -> 支持的协议 code 列表（含 CUSTOM/OTHER）
 *  - defaultCode        后端列表首项，作为新建表单默认值
 *
 * 模型类型的名称与下拉选项统一来自 /api/common/enums（useEnumOptions / EnumControl），
 * 不再走本 composable。
 *
 * 用法：
 *   const { protocolsOf, defaultCode, loadOptions } = useModelTypes();
 */
const TTL = 5 * 60 * 1000;

const loading = ref(false);
const protocolMap = ref({});
let defaultTypeCode = "";
let loadedAt = 0;
let inflight = null;

function normalizeCode(value) {
  return String(value ?? "").trim().toUpperCase();
}

function normalizeProtocols(value) {
  if (!Array.isArray(value)) {
    return [];
  }
  return Array.from(new Set(value.map(normalizeCode).filter(Boolean)));
}

function applyList(list) {
  protocolMap.value = Object.fromEntries(
    list.map((item) => [item.code, normalizeProtocols(item.supportedProtocols)])
  );
  defaultTypeCode = list[0]?.code || "";
  loadedAt = Date.now();
}

/**
 * 加载模型类型协议规则；共享缓存，并发调用只发一次请求
 * @param {boolean} force 是否忽略缓存强制刷新
 */
async function loadOptions(force = false) {
  if (!force && loadedAt && Date.now() - loadedAt <= TTL) {
    return;
  }
  if (inflight) {
    return await inflight;
  }
  loading.value = true;
  inflight = listModelTypes()
    .then((data) => {
      applyList(Array.isArray(data) ? data : []);
    })
    .catch(() => {
      // 加载失败时保留上一次成功的数据，避免界面闪空
    })
    .finally(() => {
      loading.value = false;
      inflight = null;
    });
  return await inflight;
}

export function useModelTypes() {
  /**
   * 模型类型 code -> supportedProtocols
   * @param {string} code
   * @param {string[]} [fallback] 未命中时的兜底协议，缺省空数组
   */
  function protocolsOf(code, fallback = []) {
    const protocols = protocolMap.value[normalizeCode(code)];
    return protocols?.length ? protocols : fallback;
  }

  /** 后端列表首项，作为新建表单的默认值 */
  const defaultCode = computed(() => defaultTypeCode);

  return { loading, loadOptions, protocolsOf, defaultCode };
}
