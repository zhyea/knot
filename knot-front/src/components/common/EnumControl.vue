<template>
  <!-- 下拉：单选 / 多选 -->
  <el-select
    v-if="display === 'select'"
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
      :key="item.value"
      :label="optionLabel(item)"
      :value="item.value"
    />
  </el-select>

  <!-- 单选框组 -->
  <el-radio-group
    v-else-if="display === 'radio'"
    :model-value="modelValue"
    :disabled="disabled"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-radio
      v-for="item in filteredOptions"
      :key="item.value"
      :value="item.value"
    >{{ optionLabel(item) }}</el-radio>
  </el-radio-group>

  <!-- 复选框组 -->
  <el-checkbox-group
    v-else
    :model-value="checkboxValue"
    :disabled="disabled"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-checkbox
      v-for="item in filteredOptions"
      :key="item.value"
      :value="item.value"
    >{{ optionLabel(item) }}</el-checkbox>
  </el-checkbox-group>
</template>

<script setup>
import { computed } from "vue";
import { useEnumOptions } from "../../composables/useEnumOptions";

/**
 * 后端代码枚举的统一控件（数据源 GET /api/common/enums，见 useEnumOptions）。
 *
 * - display="select"   下拉框，multiple 控制单选/多选（默认）
 * - display="radio"    单选框组
 * - display="checkbox" 复选框组（v-model 恒为数组）
 *
 * 枚举键目前有 "ModelTypeEnum"、"ModelApiProtocolEnum"；
 * DB 可配置枚举（ks_enum_configs）不归它管，继续用 EnumSelect。
 */
const props = defineProps({
  modelValue: { type: [String, Number, Array], default: "" },
  /** 代码枚举键，如 ModelTypeEnum */
  enumName: { type: String, required: true },
  /** 展示形态：select | radio | checkbox */
  display: {
    type: String,
    default: "select",
    validator: (value) => ["select", "radio", "checkbox"].includes(value)
  },
  /** 仅 display="select" 生效：多选 */
  multiple: { type: Boolean, default: false },
  placeholder: { type: String, default: "请选择" },
  clearable: { type: Boolean, default: false },
  filterable: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  collapseTags: { type: Boolean, default: false },
  collapseTagsTooltip: { type: Boolean, default: false },
  selectStyle: { type: [String, Object], default: () => ({ width: "100%" }) },
  /** 仅展示指定 code 列表 */
  includeCodes: { type: Array, default: null },
  /** 展示时附带编码，如「对话 (CHAT)」 */
  showCode: { type: Boolean, default: false }
});

const emit = defineEmits(["update:modelValue"]);

const { optionsOf, loadEnums } = useEnumOptions();

const filteredOptions = computed(() => optionsOf(props.enumName, props.includeCodes));

/** 复选框组的 v-model 必须是数组；单值时视为单元素数组，保证受控 */
const checkboxValue = computed(() => {
  if (Array.isArray(props.modelValue)) {
    return props.modelValue;
  }
  return props.modelValue === null || props.modelValue === undefined || props.modelValue === ""
    ? []
    : [props.modelValue];
});

function optionLabel(item) {
  return props.showCode ? `${item.label} (${item.value})` : item.label;
}

function syncOptions() {
  loadEnums();
}

syncOptions();
</script>
