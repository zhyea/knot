import { onMounted, ref } from "vue";
import { listProviderProfiles } from "../api/providerProfiles";

/**
 * 供应商类型下拉：来源 = 供应商信息表（kb_providers），不再走枚举分类 provider_type。
 * value 用 code 的大写形式（与 provider_type 历史取值、网关请求适配器枚举一致），label 用 name。
 */
export function useProviderTypeOptions() {
  const options = ref([]);
  const labelMap = ref({});

  function toValue(code) {
    return String(code || "").trim().toUpperCase();
  }

  async function loadOptions() {
    try {
      const result = await listProviderProfiles({ pageNum: 1, pageSize: 500 });
      const list = Array.isArray(result) ? result : result?.list || [];
      options.value = list.map((item) => ({
        value: toValue(item.code),
        label: item.name || item.code
      }));
      labelMap.value = Object.fromEntries(
        list.map((item) => [toValue(item.code), item.name || item.code])
      );
    } catch {
      options.value = [];
      labelMap.value = {};
    }
  }

  function labelOf(code) {
    if (!code) return "-";
    return labelMap.value[toValue(code)] || code;
  }

  onMounted(loadOptions);

  return { options, labelMap, loadOptions, labelOf };
}
