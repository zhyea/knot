<template>
  <RemoteEntitySelect
    v-bind="$attrs"
    :model-value="modelValue"
    :value-key="valueKey"
    :load-function="loadOptions"
    :label-function="providerAccountLabel"
    :selected-options="mergedSelectedOptions"
    :extra-params="{ enabled: true }"
    :code-only="codeOnly"
    code-key="code"
    @update:model-value="emit('update:modelValue', $event)"
    @change="onChange"
  />
</template>

<script setup lang="ts">
import {computed, ref, watch, type PropType} from "vue";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import {getProviderAccountOption, listProviderAccounts} from "@/api/providers";
import {mergeOptionList, normalizeOptionList} from "@/utils/options";
import type {Dict, Row} from "@/types";

defineOptions({ inheritAttrs: false });

const props = defineProps({
  modelValue: { type: [String, Number] as PropType<string | number | null>, default: null },
  selectedOptions: { type: Array as PropType<Row[]>, default: (): Row[] => [] },
  /** 选中值取账户行的哪个字段：id 或 code；内部匹配必须与之一致，否则会把 code 当 id 传给后端 */
  valueKey: { type: String, default: "id" },
  /** 仅展示账户 code（忽略供应商前缀与名称） */
  codeOnly: { type: Boolean, default: false }
});

const emit = defineEmits(["update:modelValue", "change"]);
const options = ref<Row[]>([]);
const selectedAccount = ref<Row | null>(null);

const mergedSelectedOptions = computed(() =>
  mergeOptionList(
    mergeOptionList(props.selectedOptions, options.value, props.valueKey),
    selectedAccount.value ? [selectedAccount.value] : [],
    props.valueKey
  )
);

watch(
  () => props.modelValue,
  async (value) => {
    if (value == null) {
      selectedAccount.value = null;
      return;
    }
    const existing = options.value.find((item) => String(item[props.valueKey]) === String(value));
    if (existing) {
      selectedAccount.value = {...existing};
      return;
    }
    if (props.valueKey !== "id") {
      // code 等非 id 值没有按值反查的接口，走列表 keyword 查询兜底
      const account = await findAccountByValue(value);
      if (String(props.modelValue) === String(value) && account) {
        selectedAccount.value = account;
        options.value = mergeOptionList(options.value, [account], props.valueKey);
      }
      return;
    }
    const account = await getProviderAccountOption(value);
    if (String(props.modelValue) === String(value) && account) {
      const normalized = {...account};
      selectedAccount.value = normalized;
      options.value = mergeOptionList(options.value, [normalized], props.valueKey);
    }
  },
  { immediate: true }
);

async function findAccountByValue(value: string | number): Promise<Row | null> {
  try {
    const data = await listProviderAccounts({pageNum: 1, pageSize: 50, keyword: String(value)});
    const list = normalizeOptionList(data);
    return list.find((item) => String(item[props.valueKey]) === String(value)) || null;
  } catch {
    return null;
  }
}

async function loadOptions(params: Dict): Promise<unknown> {
  const data = await listProviderAccounts(params);
  const list = normalizeOptionList(data);
  options.value = mergeOptionList(options.value, list, props.valueKey);
  const currentValue = props.modelValue;
  const current = list.find((item) => String(item[props.valueKey]) === String(currentValue));
  if (current && currentValue != null) {
    selectedAccount.value = {...current};
  }
  return data;
}

function providerAccountLabel(account: Row): string {
  // 括号内展示供应商名称（如 OpenAI），与列表"供应商"列口径一致；type 只是品牌 code（小写），不直接展示
  const vendor = account?.providerName || account?.type || "未知供应商";
  const accountName = account?.code || `#${account?.id}`;
  return `（${vendor}）${accountName}`;
}

function onChange(value: unknown, account: Row | null): void {
  emit("change", account || null, value);
}
</script>
