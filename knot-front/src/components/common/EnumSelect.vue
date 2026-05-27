<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder"
    :clearable="clearable"
    :filterable="filterable"
    :disabled="disabled"
    :multiple="multiple"
    :collapse-tags="collapseTags"
    :collapse-tags-tooltip="collapseTagsTooltip"
    :style="selectStyle"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-option
      v-for="item in filteredOptions"
      :key="item.itemCode"
      :label="optionLabel(item)"
      :value="item.itemCode"
    />
  </el-select>
</template>

<script setup>
import { computed, onMounted, watch } from "vue";
import { useEnums } from "../../composables/useEnums";

const props = defineProps({
  modelValue: { type: [String, Number, Array], default: "" },
  /** 枚举分类，如 provider_type、strategy_type */
  category: { type: String, required: true },
  placeholder: { type: String, default: "请选择" },
  clearable: { type: Boolean, default: false },
  filterable: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  multiple: { type: Boolean, default: false },
  collapseTags: { type: Boolean, default: false },
  collapseTagsTooltip: { type: Boolean, default: false },
  selectStyle: { type: [String, Object], default: () => ({ width: "100%" }) },
  /** 仅展示指定 itemCode 列表 */
  includeCodes: { type: Array, default: null },
  /** 展示时附带编码，如「固定 (FIXED)」 */
  showCode: { type: Boolean, default: false },
  enabledOnly: { type: Boolean, default: true }
});

const emit = defineEmits(["update:modelValue"]);

const { options, loadOptions } = useEnums(props.category, props.enabledOnly);

const filteredOptions = computed(() => {
  let list = options.value;
  if (props.includeCodes?.length) {
    const set = new Set(props.includeCodes);
    list = list.filter((i) => set.has(i.itemCode));
  }
  return list;
});

function optionLabel(item) {
  if (props.showCode) {
    return `${item.itemLabel} (${item.itemCode})`;
  }
  return item.itemLabel;
}

watch(() => props.category, () => loadOptions());

onMounted(() => loadOptions());
</script>
