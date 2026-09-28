<template>
  <RemoteEntitySelect
    v-bind="$attrs"
    :model-value="modelValue"
    :load-function="loadOptions"
    :label-function="providerAccountLabel"
    :selected-options="mergedSelectedOptions"
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
  selectedOptions: { type: Array as PropType<Row[]>, default: (): Row[] => [] }
});

const emit = defineEmits(["update:modelValue", "change"]);
const options = ref<Row[]>([]);
const selectedAccount = ref<Row | null>(null);

const mergedSelectedOptions = computed(() =>
  mergeOptionList(
    mergeOptionList(props.selectedOptions, options.value),
    selectedAccount.value ? [selectedAccount.value] : []
  )
);

watch(
  () => props.modelValue,
  async (id) => {
    if (id == null) {
      selectedAccount.value = null;
      return;
    }
    const existing = options.value.find((item) => String(item.id) === String(id));
    if (existing) {
      selectedAccount.value = {...existing, id};
      return;
    }
    const account = await getProviderAccountOption(id);
    if (String(props.modelValue) === String(id) && account) {
      const normalized = {...account, id};
      selectedAccount.value = normalized;
      options.value = mergeOptionList(options.value, [normalized]);
    }
  },
  { immediate: true }
);

async function loadOptions(params: Dict): Promise<unknown> {
  const data = await listProviderAccounts(params);
  const list = normalizeOptionList(data);
  options.value = mergeOptionList(options.value, list);
  const currentId = props.modelValue;
  const current = list.find((item) => String(item.id) === String(currentId));
  if (current && currentId != null) {
    selectedAccount.value = {...current, id: currentId};
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
