<template>
  <RemoteEntitySelect
    v-bind="$attrs"
    :model-value="modelValue"
    :load-function="loadOptions"
    :selected-options="selectedOptions"
    :extra-params="{enabledOnly: true}"
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
 *
 * <p>取值恒为 {@code OptionItem.value}（= 账户 code），Base URL 恒读 {@code selected.meta.baseUrl}。
 * 历史上的 valueKey / codeOnly / codeKey 兼容 prop 已随 options 契约移除顶层 {@code code} 而删除。</p>
 */
const props = defineProps({
  modelValue: {type: [String, Number] as PropType<string | number | null>, default: null},
  selectedOptions: {type: Array as PropType<Row[]>, default: (): Row[] => []}
});

const emit = defineEmits(["update:modelValue", "change"]);

const loadOptions = toOptionsLoader(listProviderAccountOptions);

function onChange(value: unknown, account: Row | null): void {
  emit("change", account || null, value);
}
</script>
