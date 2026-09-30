<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    size="56%"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="!pricing">
      <el-empty description="该规则未配置高低峰参数"/>
    </template>
    <template v-else>
      <el-descriptions border size="small" :column="2" class="peak-detail__meta">
        <el-descriptions-item label="供应商">{{ rule?.providerName || "全局" }}</el-descriptions-item>
        <el-descriptions-item label="统一模型">{{ rule?.logicalModelName || "默认" }}</el-descriptions-item>
        <el-descriptions-item label="计费单位">{{ rule?.unit || "-" }}</el-descriptions-item>
        <el-descriptions-item label="币种">{{ rule?.currency || "-" }}</el-descriptions-item>
        <el-descriptions-item label="判定时区">{{ pricing.timezone }}</el-descriptions-item>
        <el-descriptions-item label="规则条数">{{ pricing.phases.length }} 条（{{ peakRows.length }} 条高峰 + 1 条兜底）</el-descriptions-item>
      </el-descriptions>

      <div class="peak-detail__hint">
        时段按 {{ pricing.timezone }} 解释、左闭右开；「午夜」表示覆盖到当日 24:00（= 次日 0 点）。
        最终单价 = 模式层基础价 × 命中相位倍率。首期节假日日历为空，不会命中。
      </div>

      <el-table :data="peakRows" border size="small" stripe>
        <el-table-column label="序号" width="64" align="center">
          <template #default="{ $index }">第 {{ $index + 1 }} 条</template>
        </el-table-column>
        <el-table-column label="相位" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.phase === 'PEAK' ? 'warning' : 'success'" size="small">
              {{ PEAK_PHASE_LABELS[row.phase] || row.phase }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="倍率" width="80" align="right">
          <template #default="{ row }">{{ row.multiplier ?? "-" }}</template>
        </el-table-column>
        <el-table-column label="适用星期" min-width="150">
          <template #default="{ row }">{{ weekdayText(row) }}</template>
        </el-table-column>
        <el-table-column :label="`时段（${pricing.timezone}，左闭右开）`" min-width="200">
          <template #default="{ row }">{{ describePeakWindows(row) }}</template>
        </el-table-column>
      </el-table>

      <div class="peak-detail__timeline">
        <div class="peak-detail__timeline-head">
          <span class="peak-detail__label">一周时间轴</span>
          <span class="peak-detail__hint-inline">高峰时段（{{ pricing.timezone }}）</span>
        </div>
        <div v-for="day in PEAK_WEEKDAYS" :key="day.code" class="peak-detail__track-row">
          <span class="peak-detail__day">{{ day.label }}</span>
          <div class="peak-detail__track">
            <span
              v-for="hour in 24"
              :key="hour"
              class="peak-detail__cell"
              :class="{ 'peak-detail__cell--peak': isPeakHour(day.code, hour - 1) }"
              :title="`${day.label} ${String(hour - 1).padStart(2, '0')}:00`"
            />
          </div>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import {computed} from "vue";
import type {Row} from "@/types";
import {
  PEAK_PHASE_LABELS,
  PEAK_WEEKDAYS,
  coversHour,
  coversWeekday,
  describePeakWindows,
  parsePeakPricing
} from "@/utils/billingPeakOffPeak";
import type {PeakPhaseRow} from "@/utils/billingPeakOffPeak";
import {parseJsonObject} from "@/utils/format";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  /** 高低峰所属的计费规则（当前版本行） */
  rule: {type: Object as () => Row | null, default: null}
});

const emit = defineEmits(["update:modelValue"]);

const title = computed(() => `高低峰明细 - ${props.rule?.code || `#${props.rule?.id ?? ""}`}`);
const pricing = computed(() => parsePeakPricing(parseJsonObject(props.rule?.configJson).pricing));
/** 全部相位规则（含末条兜底），顺序即判定顺序 */
const peakRows = computed<PeakPhaseRow[]>(() => pricing.value?.phases || []);
/** 仅高峰规则，供时间轴使用 */
const peakOnly = computed(() => peakRows.value.filter((row) => !row.isDefault && row.phase === "PEAK"));

function weekdayText(row: PeakPhaseRow): string {
  if (row.isDefault) {
    return "不限";
  }
  if (!row.weekdays.length) {
    return "未选星期";
  }
  return PEAK_WEEKDAYS.filter((day) => row.weekdays.includes(day.code)).map((day) => day.label).join("、");
}

/** 任一高峰规则在该星期该小时生效即高亮（多规则取并集，修正原先只看第一条的摘要） */
function isPeakHour(weekdayCode: string, hour: number): boolean {
  return peakOnly.value.some((row) => coversWeekday(row, weekdayCode) && coversHour(row, hour));
}
</script>

<style scoped>
.peak-detail__meta {
  margin-bottom: 12px;
}

.peak-detail__hint,
.peak-detail__hint-inline {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 20px;
}

.peak-detail__hint {
  margin-bottom: 12px;
}

.peak-detail__timeline {
  margin-top: 16px;
}

.peak-detail__timeline-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 8px;
}

.peak-detail__label {
  font-size: 14px;
  font-weight: 600;
}

.peak-detail__track-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.peak-detail__day {
  flex: 0 0 40px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.peak-detail__track {
  display: flex;
  flex: 1;
  gap: 1px;
}

.peak-detail__cell {
  flex: 1;
  height: 14px;
  border-radius: 2px;
  background: var(--el-fill-color);
}

.peak-detail__cell--peak {
  background: var(--el-color-warning-light-3);
}
</style>
