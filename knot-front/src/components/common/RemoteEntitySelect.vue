<template>
  <el-select
    v-bind="$attrs"
    :model-value="modelValue"
    :multiple="multiple"
    filterable
    remote
    reserve-keyword
    :remote-method="search"
    :loading="loading"
    @update:model-value="onUpdate"
    @change="onChange"
    @visible-change="onVisibleChange"
  >
    <el-option
      v-for="item in mergedOptions"
      :key="item[valueKey]"
      :label="labelFunction(item)"
      :value="item[valueKey]"
    />
  </el-select>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { normalizeOptionList } from "../../utils/options";

defineOptions({ inheritAttrs: false });

const props = defineProps({
  modelValue: { type: [String, Number, Array], default: null },
  loadFunction: { type: Function, required: true },
  labelFunction: { type: Function, required: true },
  selectedOptions: { type: Array, default: () => [] },
  extraParams: { type: Object, default: () => ({}) },
  valueKey: { type: String, default: "id" },
  multiple: { type: Boolean, default: false }
});

const emit = defineEmits(["update:modelValue", "change"]);

const loading = ref(false);
const options = ref([]);
let searchTimer = null;

const mergedOptions = computed(() => {
  const map = new Map();
  for (const item of props.selectedOptions || []) {
    if (item && item[props.valueKey] != null) {
      map.set(item[props.valueKey], item);
    }
  }
  for (const item of options.value) {
    if (item && item[props.valueKey] != null) {
      map.set(item[props.valueKey], item);
    }
  }
  return Array.from(map.values());
});

watch(
  () => props.extraParams,
  () => loadOptions(""),
  { deep: true }
);

async function loadOptions(keyword = "") {
  loading.value = true;
  try {
    const result = await props.loadFunction({
      pageNum: 1,
      pageSize: 10,
      keyword: keyword?.trim() || undefined,
      ...props.extraParams
    });
    const list = normalizeOptionList(result);
    options.value = list.slice(0, 10);
  } finally {
    loading.value = false;
  }
}

function search(keyword) {
  if (searchTimer) {
    clearTimeout(searchTimer);
  }
  searchTimer = setTimeout(() => loadOptions(keyword), 250);
}

function onUpdate(value) {
  emit("update:modelValue", value);
}

function onChange(value) {
  emit("change", value);
}

function onVisibleChange(visible) {
  if (visible && options.value.length === 0) {
    loadOptions("");
  }
}

loadOptions("");
</script>
