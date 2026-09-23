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

<script setup>
import { computed, ref, watch } from "vue";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import { getProviderAccountOption, listProviderAccounts } from "../../api/providers";
import { mergeOptionList, normalizeOptionList } from "../../utils/options";

defineOptions({ inheritAttrs: false });

const props = defineProps({
  modelValue: { type: [String, Number], default: null },
  selectedOptions: { type: Array, default: () => [] }
});

const emit = defineEmits(["update:modelValue", "change"]);
const options = ref([]);

const mergedSelectedOptions = computed(() => mergeOptionList(props.selectedOptions, options.value));

watch(
  () => props.modelValue,
  async (id) => {
    if (id == null || options.value.some((item) => item.id === id)) {
      return;
    }
    const account = await getProviderAccountOption(id);
    if (props.modelValue === id && account) {
      options.value = mergeOptionList(options.value, [account]);
    }
  },
  { immediate: true }
);

async function loadOptions(params) {
  const data = await listProviderAccounts(params);
  options.value = mergeOptionList(options.value, normalizeOptionList(data));
  return data;
}

function providerAccountLabel(account) {
  const type = account?.type || account?.providerName || "未知类型";
  const accountName = account?.code || `#${account?.id}`;
  return `（${type}）${accountName}`;
}

function onChange(value, account) {
  emit("change", account || null, value);
}
</script>
