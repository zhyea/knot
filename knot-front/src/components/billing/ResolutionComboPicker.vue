<template>
  <div class="combo-picker">
    <div class="combo-picker__head">
      <span class="combo-picker__title">计费组合</span>
      <span class="combo-picker__tip" :class="{'is-locked': disabled}">{{ tip }}</span>
    </div>
    <el-checkbox-group :model-value="modelValue" @update:model-value="onChange">
      <el-checkbox
        v-for="combo in RESOLUTION_COMBOS"
        :key="combo.code"
        :value="combo.code"
        :disabled="disabled || combo.code === 'BASE'"
      >
        {{ combo.label }}
        <span v-if="combo.code === 'BASE'" class="combo-picker__required">（必选）</span>
      </el-checkbox>
    </el-checkbox-group>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {RESOLUTION_COMBOS, normalizeCombos} from "@/utils/billingResolution";
import type {ResolutionComboCode} from "@/utils/billingResolution";

/**
 * 计费组合选择器：**先定维度组合，再逐行加档位**。
 *
 * <p>⭐ 与「选档位」相反——档位名不可枚举、逐行自由新增；组合才是先定下来的维度轴。
 * 组合决定档位表显示哪些价格列、落哪些后缀键（见 utils/billingResolution 的 RESOLUTION_COMBOS）。</p>
 *
 * <p>组合一旦有档位就锁死：改组合会让已填价格错位到别的键（无声价被当成有声价），
 * 故 {@code disabled} 由宿主按「是否已有档位」传入，本组件只负责呈现与回传。</p>
 *
 * <p>BASE（无声）恒勾选且不可取消：它是逐级回落链的终点，缺了它无声请求无价可取。</p>
 */
const props = defineProps({
  modelValue: {type: Array as () => ResolutionComboCode[], required: true},
  /** 已有档位后锁定：宿主传 true，所有组合不可再勾选/取消 */
  disabled: {type: Boolean, default: false}
});
const emit = defineEmits<{(e: "update:modelValue", value: ResolutionComboCode[]): void}>();

const tip = computed(() =>
  props.disabled
    ? "已有档位，计费组合已锁定；如需调整请先删除全部档位"
    : "勾选要定价的组合，新增档位只显示已选组合的价格列"
);

function onChange(value: (string | number | boolean)[]) {
  emit("update:modelValue", normalizeCombos(value));
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

.combo-picker__required {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
