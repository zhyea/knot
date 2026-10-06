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
import {computed, getCurrentInstance, onBeforeUnmount, ref, useAttrs, watch, type PropType} from "vue";
import {MISSING_OPTION_LABEL_PREFIX, normalizeOptionList, OPTION_PAGE_SIZE} from "@/utils/options";
import {useMissingOptionReporter} from "@/composables/useMissingOptionGuard";
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
const missingValues = ref<string[]>([]);
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
  // 缺失项（已删除 / 无权限）：渲染为禁用占位项，让用户看得见（而非 el-select 回退成裸 id）
  for (const v of missingValues.value) {
    map.set(v, {
      [props.valueKey]: v,
      label: MISSING_OPTION_LABEL_PREFIX + v + "）",
      disabled: true,
      missing: true
    });
  }
  return Array.from(map.values());
});

/** 存在缺失值（已选项被删除或无权限）——父级提交前可据此阻止非法提交。 */
function hasMissingValues(): boolean {
  return missingValues.value.length > 0;
}

defineExpose({hasMissingValues});

watch(extraParamsSignature, () => {
  options.value = [];
  // ⚠ 必须连missingValues 一起清：筛选上下文变了（如 ModelPoolFormDrawer 换统一模型、
  // RoutingRuleFormDrawer 切 targetType），旧上下文的缺失项已不适用当前表单。
  // 只清 options 会让守卫拿着过期 missingValues 持续误拦提交，且下拉渲染幽灵占位项。
  missingValues.value = [];
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
    const kw = keyword?.trim() || "";
    // 仅在无关键字（首屏 / 回显）时带 values：否则回显项会混进关键字搜索结果
    const values = kw ? [] : currentValues();
    const result = await props.loadFunction({
      pageNum: 1,
      pageSize: OPTION_PAGE_SIZE,
      keyword: kw || undefined,
      ...(values.length ? {values} : {}),
      ...props.extraParams
    });
    // ⚠ 不要 slice：后端 assemble 把不在首页的回显项 append 在 list 尾部，
    // 首页满页时 slice 会把回显项整段切掉，「已选项不在第一页也能回显」随之失效。
    options.value = normalizeOptionList(result);
    missingValues.value = extractMissing(result);
    emit("missing-values", missingValues.value);
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
  // 缺失占位项恒显示提示文本，不受 codeOnly / codeKey 影响
  if (item?.missing === true) {
    return String(item?.label ?? "");
  }
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

/** 缺失值上报目标（由所在表单通过 useMissingOptionGuard 声明；无守卫时 no-op）。 */
const missingSink = useMissingOptionReporter();
/** 上报用的可读名：透传下来的 placeholder（在 $attrs 里，不在 props），缺省「选项」。 */
const attrs = useAttrs();
const label = computed(() => {
  const ph = attrs.placeholder;
  return typeof ph === "string" && ph ? ph : "选项";
});
/**
 * 上报键：必须按**组件实例**唯一，不能用 placeholder。
 *
 * <p>守卫内部以 key 聚合同一表单内多个下拉的缺失项，早期用 placeholder 当 key，
 * 隐式依赖「同一抽屉内 placeholder 全局唯一」。一旦某个抽屉出现两个同名 placeholder
 * 的下拉（如两个「请选择模型」），后上报者会覆盖前者丢失告警，且删除时报错key 也只清一个。
 * 这里用 Vue 内部 uid（组件实例唯一）作键彻底消除该耦合。</p>
 *
 * <p>⚠ uid 必须在 setup **同步期**取出（{@code getCurrentInstance} 依赖当前实例上下文，
 * 放进 computed 惰性求值时可能已脱离上下文返回 null，导致所有实例 key 都退化成 #0 又撞车）。
 * 标签文本仍用 computed，因为 $attrs.placeholder 可能随父组件变化。</p>
 */
const instanceUid = getCurrentInstance()?.uid ?? 0;
const reportKey = computed(() => `${label.value}#${instanceUid}`);

watch(missingValues, (values) => missingSink?.report(reportKey.value, values), {immediate: true});

/**
 * reportKey 变化（父组件动态改 placeholder）时必须清掉旧键，否则守卫里会留下
 * 一条永远不消失的幽灵缺失项，持续误拦提交。缺失项本身不变，只是归属键变了。
 */
watch(reportKey, (nextKey, prevKey) => {
  if (prevKey && missingValues.value.length > 0) {
    missingSink?.report(prevKey, []);
    missingSink?.report(nextKey, missingValues.value);
  }
});

onBeforeUnmount(() => {
  missingSink?.report(reportKey.value, []);
  if (searchTimer) {
    clearTimeout(searchTimer);
    searchTimer = null;
  }
});
</script>
