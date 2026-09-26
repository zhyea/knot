import { computed, ref } from "vue";
import { listDebugCapabilities } from "../api/routing";

/**
 * 路由调试协议能力数据源（GET /api/routing-rules/debug-capabilities）
 *
 * 单一数据源，承载调试面板全部协议行为元数据，前端不再维护：
 *  - gatewayPathOf(code)   网关调试路径（替代 PROTOCOL_PATHS）
 *  - canonicalOf(code)     供应商别名归一（替代 PROTOCOL_CANONICAL_MAP）
 *  - hintOf(code)          协议说明文案（替代 PROTOCOL_HINTS）
 *  - templateOf(code)      默认请求体模板（{{model}} / {{prompt}} 占位，替代请求体 switch）
 *  - promptFieldOf(code)   请求体中承载 prompt 的字段路径（替代 inferPrompt switch）
 *
 * 用法：
 *   const { loadOptions, capabilityOf } = useDebugCapabilities();
 */
const TTL = 5 * 60 * 1000;

const loading = ref(false);
const capabilityMap = ref({});
let loadedAt = 0;
let inflight = null;

function normalizeCode(value) {
  return String(value ?? "").trim().toUpperCase();
}

function applyList(list) {
  capabilityMap.value = Object.fromEntries(
    (Array.isArray(list) ? list : []).map((item) => {
      const code = normalizeCode(item?.code);
      return [
        code,
        {
          code,
          canonicalCode: normalizeCode(item?.canonicalCode || item?.code),
          gatewayPath: item?.gatewayPath || "",
          hint: item?.hint || "",
          defaultRequestBody: item?.defaultRequestBody || null,
          promptField: item?.promptField || null
        }
      ];
    })
  );
  loadedAt = Date.now();
}

/**
 * 加载调试能力；共享缓存，并发调用只发一次请求
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
  inflight = listDebugCapabilities()
    .then(applyList)
    .catch(() => {
      // 加载失败时保留上一次成功的数据，避免界面闪空
    })
    .finally(() => {
      loading.value = false;
      inflight = null;
    });
  return await inflight;
}

export function useDebugCapabilities() {
  const loaded = computed(() => Object.keys(capabilityMap.value).length > 0);

  function capabilityOf(code) {
    return capabilityMap.value[normalizeCode(code)] || null;
  }

  function canonicalOf(code) {
    const normalized = normalizeCode(code);
    return capabilityMap.value[normalized]?.canonicalCode || normalized;
  }

  function gatewayPathOf(code) {
    return capabilityMap.value[normalizeCode(code)]?.gatewayPath || "";
  }

  function hintOf(code) {
    return capabilityMap.value[normalizeCode(code)]?.hint || "";
  }

  function templateOf(code) {
    return capabilityMap.value[normalizeCode(code)]?.defaultRequestBody || null;
  }

  function promptFieldOf(code) {
    return capabilityMap.value[normalizeCode(code)]?.promptField || null;
  }

  return { loading, loadOptions, loaded, capabilityOf, canonicalOf, gatewayPathOf, hintOf, templateOf, promptFieldOf };
}

/**
 * 用实际取值替换模板中的 {{model}} / {{prompt}} 占位符（深度遍历）
 */
export function hydrateTemplate(template, model, prompt) {
  if (typeof template === "string") {
    return template
      .replaceAll("{{model}}", model || "model-name")
      .replaceAll("{{prompt}}", prompt || "");
  }
  if (Array.isArray(template)) {
    return template.map((item) => hydrateTemplate(item, model, prompt));
  }
  if (template && typeof template === "object") {
    return Object.fromEntries(
      Object.entries(template).map(([key, value]) => [key, hydrateTemplate(value, model, prompt)])
    );
  }
  return template;
}

/**
 * 按 promptField 路径从请求体中提取 prompt。
 * 支持 "input" / "prompt" / "query"（字符串或数组首项）与 "messages[0].content" 形式。
 */
export function extractPrompt(body, promptField) {
  if (!promptField) {
    return null;
  }
  const indexed = promptField.match(/^(\w+)\[(\d+)\](?:\.(\w+))?$/);
  if (indexed) {
    const list = body?.[indexed[1]];
    if (!Array.isArray(list)) {
      return null;
    }
    const item = list[Number(indexed[2])];
    const value = indexed[3] ? item?.[indexed[3]] : item;
    return typeof value === "string" ? value : null;
  }
  const value = body?.[promptField];
  if (typeof value === "string") {
    return value;
  }
  if (Array.isArray(value)) {
    return typeof value[0] === "string" ? value[0] : null;
  }
  return null;
}
