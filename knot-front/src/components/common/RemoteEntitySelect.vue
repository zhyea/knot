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
      :key="keyOf(item)"
      :label="optionLabel(item)"
      :value="item[valueKey]"
      :disabled="item.disabled === true"
    />
  </el-select>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch, type PropType} from "vue";
import {normalizeOptionList} from "@/utils/options";
import type {Dict, Row} from "@/types";

defineOptions({inheritAttrs: false});

const props = defineProps({
  modelValue: {type: [String, Number, Array] as PropType<string | number | unknown[] | null>, default: null},
  loadFunction: {type: Function, required: true},
  /** 新 options 契约可用 labelKey 直接取 label，此时可不传 */
  labelFunction: {type: Function, default: () => (item: Row) => String(item?.label ?? "")},
  selectedOptions: {type: Array as PropType<Row[]>, default: (): Row[] => []},
  extraParams: {type: Object, default: () => ({})},
  valueKey: {type: String, default: "id"},
  multiple: {type: Boolean, default: false},
  /** 仅展示 code（忽略 labelFunction）；取 codeKey 字段，缺省取 valueKey */
  codeOnly: {type: Boolean, default: true},
  codeKey: {type: String, default: null},
  /** 直接取该字段作为 label（新 options 契约：value-key="value" + label-key="label"）；缺省仍走 labelFunction */
  labelKey: {type: String, default: null}
});

const emit = defineEmits(["update:modelValue", "change", "missing-values"]);

const loading = ref(false);
const options = ref<Row[]>([]);
const dropdownVisible = ref(false);
const extraParamsSignature = computed(() => JSON.stringify(props.extraParams ?? {}));
let searchTimer: ReturnType<typeof setTimeout> | null = null;

/** 统一键：后端 missingValues 为字符串，id 型 modelValue 为数字，此处归一避免错配。 */
function keyOf(item: Row): string {
  return String(item?.[props.valueKey]);
}

const mergedOptions = computed(() => {
  const map = new Map<string, Row>();
  for (const item of props.selectedOptions || []) {
    if (item && item[props.valueKey] != null) {
      map.set(keyOf(item), item);
    }
  }
  for (const item of options.value) {
    if (item && item[props.valueKey] != null) {
      map.set(keyOf(item), item);
    }
  }
  return Array.from(map.values());
});

watch(extraParamsSignature, () => {
  options.value = [];
  if (dropdownVisible.value) {
    loadOptions("");
  }
});

/** 当前已选值（作为 values 传给后端，用于回显不在当前页的已选项）。 */
function currentValues(): string[] {
  const mv = props.modelValue;
  if (Array.isArray(mv)) {
    return mv.filter((v) => v != null && v !== "").map((v) => String(v));
  }
  if (mv != null && mv !== "") {
    return [String(mv)];
  }
  return [];
}

async function loadOptions(keyword = "") {
  loading.value = true;
  try {
    const values = currentValues();
    const result = await props.loadFunction({
      pageNum: 1,
      pageSize: 10,
      keyword: keyword?.trim() || undefined,
      ...(values.length ? {values} : {}),
      ...props.extraParams
    });
    const list = normalizeOptionList(result);
    options.value = list.slice(0, 10);
    const missing = extractMissing(result);
    emit("missing-values", missing);
  } finally {
    loading.value = false;
  }
}

/** 仅新 options 契约返回 missingValues；旧 loader（PageResult）无此字段，按空处理。 */
function extractMissing(result: unknown): string[] {
  const raw = (result as Dict)?.missingValues;
  return Array.isArray(raw) ? raw.map((v) => String(v)) : [];
}

function search(keyword: string): void {
  if (searchTimer) {
    clearTimeout(searchTimer);
  }
  searchTimer = setTimeout(() => loadOptions(keyword), 250);
}

function onUpdate(value: unknown): void {
  emit("update:modelValue", value);
}

function optionLabel(item: Row): string {
  // 优先级：codeOnly(显示 code，可配) > labelKey(直接取字段) > labelFunction
  if (props.codeOnly) {
    const key = props.codeKey ?? props.valueKey;
    const code = item?.[key];
    return code == null ? "" : String(code);
  }
  if (props.labelKey) {
    const label = item?.[props.labelKey];
    return label == null ? "" : String(label);
  }
  return props.labelFunction(item);
}

function onChange(value: unknown): void {
  const selected = props.multiple
    ? mergedOptions.value.filter((item) => Array.isArray(value) && value.map((v) => String(v)).includes(keyOf(item)))
    : mergedOptions.value.find((item) => keyOf(item) === String(value)) || null;
  emit("change", value, selected);
}

function onVisibleChange(visible: boolean): void {
  dropdownVisible.value = visible;
  if (visible && options.value.length === 0) {
    loadOptions("");
  }
}

onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer);
    searchTimer = null;
  }
});
</script>
