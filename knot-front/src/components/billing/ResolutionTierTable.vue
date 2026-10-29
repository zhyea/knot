<template>
  <div class="res-table">
    <div v-if="visibleRows.length" class="res-table__head" :style="gridStyle">
      <span>档位</span>
      <span v-for="column in columns" :key="column.field">{{ column.label }}</span>
    </div>
    <div v-for="row in visibleRows" :key="row.uid || `res-table-${row.key}`" class="res-table__row" :style="gridStyle">
      <span class="res-table__name">{{ row.key }}</span>
      <el-input-number
        v-for="column in columns"
        :key="column.field"
        :model-value="row[column.field]"
        :min="0"
        :step="UNIT_PRICE_STEP"
        :placeholder="column.placeholder"
        controls-position="right"
        @update:model-value="(value: number | undefined | null) => setPrice(row, column.field, value)"
      />
    </div>
    <div v-if="!visibleRows.length" class="res-table__empty">
      {{ emptyText }}
    </div>
  </div>
</template>

<script setup lang="ts">
import {type PropType, computed} from "vue";
import {UNIT_PRICE_STEP} from "@/utils/billingPrice";
import {normalizeTierKey} from "@/utils/billingResolution";
import type {ResolutionPriceColumn, ResolutionPriceField, ResolutionRow} from "@/utils/billingResolution";

/**
 * 档位 × 定价维度价格表（两步式的第二步：只为「已选档位」填价）。
 *
 * <p>⭐ 该表最初从 Kling 方案组件里抽出：维度分档价（DIMENSION_TIERED）用的是<b>同一套键契约</b>
 * （{@code 档位} / {@code 档位:AUDIO} / {@code 档位:REFERENCE} / {@code 档位:REFERENCE:AUDIO}）。
 * 现在固定价等通用视频方案也用这张表，只是<b>列集不同</b>（{@code RESOLUTION_PRICE_COLUMNS.BASE}
 * 只要「单价 / 含参考输入」两列）——列集由方案决定，行契约共用一份，
 * 抄成两张表就会出现「同一档位行有两套创建逻辑」，加字段时必漏一处。</p>
 *
 * <p>⭐ 表的增删移到 {@link ResolutionTierPicker}：两步式下「档位」是选择出来的，
 * 价格表只负责填价，不再有「档位名留空的僵尸行」参与保存判断。
 * 行数据的创建也因此不必留在本组件。</p>
 *
 * <p>行数据仍然是宿主传进来的 {@code form.videoResolutionRows}（真实对象引用），
 * 本组件直接改行字段，不在内部另存一份——否则保存时会互相覆盖。</p>
 */
const props = defineProps({
  /** 已选档位行（由 ResolutionTierPicker 对齐过：有名字、按选择顺序、无重复） */
  rows: {type: Array as PropType<ResolutionRow[]>, default: (): ResolutionRow[] => []},
  /** 要渲染的价格列：决定「档位 × 哪些定价维度」，见 RESOLUTION_PRICE_COLUMNS */
  columns: {type: Array as PropType<ResolutionPriceColumn[]>, required: true},
  /** 未选任何档位时的引导文案 */
  emptyText: {type: String, default: "请先在上方选择支持的分辨率档位"}
});

/** 列数决定栅格：档位名占较宽的一格，价格列等宽 */
const gridStyle = computed(
  () => ({gridTemplateColumns: `minmax(96px, 1.2fr) repeat(${props.columns.length}, minmax(0, 1fr))`})
);

/** 兜底：未命名行不渲染也不落库（键空间里空名不是合法档位） */
const visibleRows = computed<ResolutionRow[]>(() =>
  props.rows.filter((row) => normalizeTierKey(row.key) !== "")
);

function setPrice(row: ResolutionRow, field: ResolutionPriceField, value: number | undefined | null) {
  row[field] = value == null ? null : value;
}
</script>

<style scoped>
.res-table {
  width: 100%;
}

.res-table__head,
.res-table__row {
  display: grid;
  gap: 8px;
  align-items: center;
}

.res-table__head {
  padding: 0 12px 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.res-table__row {
  padding: 10px 12px;
  margin-bottom: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}

.res-table__row :deep(.el-input-number) {
  width: 100%;
}

.res-table__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.res-table__empty {
  padding: 18px 12px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
