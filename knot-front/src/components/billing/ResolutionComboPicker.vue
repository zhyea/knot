<template>
  <div class="combo-picker">
    <div class="combo-picker__head">
      <span class="combo-picker__title">计费组合</span>
      <span class="combo-picker__tip" :class="{'is-locked': disabled}">{{ tip }}</span>
    </div>
    <div class="combo-picker__axes">
      <el-checkbox
        v-for="axis in AXES"
        :key="axis.code"
        :model-value="axisChecked(axis.code)"
        :disabled="disabled"
        @update:model-value="(value: string | number | boolean) => onToggle(axis.code, Boolean(value))"
      >
        {{ axis.label }}
        <span class="combo-picker__axis-hint">{{ axis.hint }}</span>
      </el-checkbox>
    </div>
    <div class="combo-picker__summary">
      基础价<b>必含</b>（回落终点）；当前价格列：
      <span class="combo-picker__columns">{{ columnLabels }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {RESOLUTION_COMBOS, hasComboAxis, normalizeCombos, withComboAxis} from "@/utils/billingResolution";
import type {ResolutionComboAxis, ResolutionComboCode} from "@/utils/billingResolution";

/**
 * 计费组合选择器：**哪个维度要单独定价**，而不是「哪些组合要勾上」。
 *
 * <p>⭐ 2026-10-30 改版的两条理由：</p>
 * <ol>
 *   <li>原来四码全勾，「无声」被当成默认意图——可 {@code BASE} 实际是
 *       「请求未声明音频开关」的价（默认带声的模型全落这里），是<b>主力价</b>，
 *       把它做成一个默认勾上的「无声」复选框，等于诱导配置者把便宜的无声音价填进主力列；</li>
 *   <li>四列全开让只有一个价的模型面对两列永远填不上的空价。真实语义是
 *       「这个维度要不要卖更贵」，默认关才对。</li>
 * </ol>
 *
 * <p>关 = 该维度与基础价同价（后端按回落链自动取值），开 = 单独定价、多一列。
 * 两个开关是**正交**的，都开时自动补 {@code REFERENCE_AUDIO}（取价链最具体档，不减不加）。
 * 基础价不作为复选框出现，改为说明文字——它是恒含项，不是「默认勾选的选项」。</p>
 *
 * <p>组合一旦有档位就锁死：改组合会让已填价格错位到别的键，故 {@code disabled}
 * 由宿主按「是否已有档位」传入，本组件只负责呈现与回传。</p>
 */
const props = defineProps({
  modelValue: {type: Array as () => ResolutionComboCode[], required: true},
  /** 已有档位后锁定：宿主传 true，两个开关都不可再动 */
  disabled: {type: Boolean, default: false}
});
const emit = defineEmits<{(e: "update:modelValue", value: ResolutionComboCode[]): void}>();

/** 两个开关的文案：勾上 = 单独定价，不勾 = 同基础价（不勾是默认、也是常见形态） */
const AXES: {code: ResolutionComboAxis; label: string; hint: string}[] = [
  {code: "AUDIO", label: "有声单独定价", hint: "不勾：有声与基础价同价"},
  {code: "REFERENCE", label: "含参考输入单独定价", hint: "不勾：有输入视频与基础价同价"}
];

const tip = computed(() =>
  props.disabled
    ? "已有档位，计费组合已锁定；如需调整请先删除全部档位"
    : "默认只用基础价，某个维度要卖更贵时才开启"
);

function axisChecked(axis: ResolutionComboAxis): boolean {
  return hasComboAxis(props.modelValue, axis);
}

/** 当前会渲染的价格列（让「开一个开关多一列」在勾之前就能看见） */
const columnLabels = computed(() =>
  RESOLUTION_COMBOS
    .filter((combo) => normalizeCombos(props.modelValue).includes(combo.code))
    .map((combo) => combo.label)
    .join(" · ")
);

function onToggle(axis: ResolutionComboAxis, on: boolean) {
  emit("update:modelValue", withComboAxis(props.modelValue, axis, on));
}
</script>

<style scoped>
.combo-picker {
  margin-bottom: 8px;
}

.combo-picker__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 2px;
}

.combo-picker__title {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.combo-picker__tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.combo-picker__tip.is-locked {
  color: var(--el-color-warning);
}

.combo-picker__axes {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.combo-picker__axis-hint {
  margin-left: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.combo-picker__summary {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.combo-picker__summary b {
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.combo-picker__columns {
  color: var(--el-text-color-regular);
}
</style>
