<template>
  <RemoteEntitySelect
    v-bind="$attrs"
    :model-value="modelValue"
    :value-key="valueKey"
    :load-function="loadOptions"
    :selected-options="selectedOptions"
    :extra-params="{enabledOnly: true}"
    :code-only="codeOnly"
    code-key="code"
    label-key="label"
    @update:model-value="emit('update:modelValue', $event)"
    @change="onChange"
  />
</template>

<script setup lang="ts">
import type {PropType} from "vue";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import {listProviderAccountOptions} from "@/api/options";
import {toOptionsLoader} from "@/utils/options";
import type {Row} from "@/types";

defineOptions({inheritAttrs: false});

/**
 * 供应商账户下拉（新 options 契约，value=code）。
 *
 * <p>已统一走 {@code POST /api/provider-accounts/options}：编辑态回显由组件自动带 values
 * 交后端按值回显，取代了旧的「按 id 调 getProviderAccountOption / keyword 反查」兜底。
 * 旧端点 {@code GET /api/provider-accounts/options/{id}} 随阶段三删除。</p>
 */
const props = defineProps({
  modelValue: {type: [String, Number] as PropType<string | number | null>, default: null},
  selectedOptions: {type: Array as PropType<Row[]>, default: (): Row[] => []},
  /** 新契约取值字段（value 与 code 同为账户 code，默认 value；保留 prop 兼容旧调用方传 "code"） */
  valueKey: {type: String, default: "value"},
  /** 仅展示账户 code；false 时展示 label（供应商 / 账户码） */
  codeOnly: {type: Boolean, default: false}
});

const emit = defineEmits(["update:modelValue", "change"]);

const loadOptions = toOptionsLoader(listProviderAccountOptions);

function onChange(value: unknown, account: Row | null): void {
  emit("change", account || null, value);
}
</script>
