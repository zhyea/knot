<template>
  <div>
    <el-table v-loading="loading" :data="rows" stripe border size="small" style="width: 100%">
      <el-table-column prop="code" label="规则编码" min-width="160" show-overflow-tooltip/>
      <el-table-column label="模型族" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.modelFamilyName || (row.modelFamilyCode ? `#${row.modelFamilyCode}` : "默认") }}
        </template>
      </el-table-column>
      <el-table-column label="版本" width="120" align="center" show-overflow-tooltip>
        <template #default="{ row }">{{ row.versionCode || "-" }}</template>
      </el-table-column>
      <el-table-column label="计费模式" width="120">
        <template #default="{ row }">{{ billingModeLabel(row.billingMode) }}</template>
      </el-table-column>
      <el-table-column label="进阶方案" width="110">
        <template #default="{ row }">{{ pricingPlanLabel(row.pricingPlan) }}</template>
      </el-table-column>
      <el-table-column label="阶梯" width="130" align="center">
        <template #default="{ row }">
          <el-button
            v-if="tierCount(row)"
            link
            type="primary"
            @click="emit('tier-detail', row)"
          >
            {{ tierCount(row) }} 档 · 明细
          </el-button>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="高低峰" width="180" align="center" show-overflow-tooltip>
        <template #default="{ row }">
          <el-button
            v-if="peakSummary(row)"
            link
            type="primary"
            :title="peakTitle(row)"
            @click="emit('peak-detail', row)"
          >
            {{ peakSummary(row) }}
          </el-button>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="currency" label="币种" width="80" align="center"/>
      <el-table-column prop="unit" label="单位" width="110"/>
      <el-table-column label="启用" width="88" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled !== false"
            :loading="togglingId === row.id"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
            @change="(value) => emit('enabled-change', row, value)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'preview', label: '试算', icon: DataAnalysis },
              { key: 'log', label: '日志', icon: Document },
              { key: 'delete', label: '删除', icon: Delete, type: 'danger', confirm: '删除后不可在列表中查看，确认删除？' }
            ]"
            @action="(action) => emit(action, row)"
          />
        </template>
      </el-table-column>
    </el-table>

    <ListPagination
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      :show-refresh="showRefresh"
      @refresh="emit('refresh')"
      @page-change="(page) => emit('page-change', page)"
      @size-change="(size) => emit('size-change', size)"
    />
  </div>
</template>

<script setup lang="ts">
import {type PropType} from "vue";
import {DataAnalysis, Delete, Document, Edit} from "@element-plus/icons-vue";
import type {Row} from "@/types";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {parseTierRows} from "@/utils/billingTier";
import {PEAK_WEEKDAYS, describePeakWindows, parsePeakPricing, toNumberOrNull} from "@/utils/billingPeakOffPeak";
import {parseJsonObject} from "@/utils/format";

defineProps({
  rows: {type: Array, default: (): Row[] => []},
  loading: {type: Boolean, default: false},
  total: {type: Number, default: 0},
  pageNum: {type: Number, default: 1},
  pageSize: {type: Number, default: 20},
  togglingId: {type: [Number, String] as PropType<number | string | null>, default: null},
  showRefresh: {type: Boolean, default: true}
});

const emit = defineEmits([
  "create",
  "edit",
  "log",
  "tier-detail",
  "peak-detail",
  "preview",
  "delete",
  "refresh",
  "enabled-change",
  "page-change",
  "size-change"
]);
const {labelOf: enumLabelOf} = useEnumOptions();

function billingModeLabel(code: unknown): string {
  return enumLabelOf("BillingModeEnum", code);
}

function pricingPlanLabel(code: unknown): string {
  return enumLabelOf("PricingPlanEnum", code, "-");
}

/** 当前版本的阶梯档数；0 表示非阶梯或无档位（列表据此隐藏明细入口） */
function tierCount(row: Row): number {
  return parseTierRows(parseJsonObject(row.configJson).tier).length;
}

/** 全部高峰规则（不含兜底）；多规则时摘要必须覆盖全部，不能只看第一条 */
function peakRows(row: Row) {
  const pricing = peakPricingOf(row);
  return pricing ? pricing.phases.filter((item) => !item.isDefault) : [];
}

/**
 * 高低峰摘要：倍率 + 规则数 + 时段总数。
 * 多规则时展示「N 条」，与明细抽屉口径一致（修正原先只取第一条导致的漏报）。
 */
function peakSummary(row: Row): string {
  const pricing = peakPricingOf(row);
  if (!pricing) {
    return "";
  }
  const peaks = peakRows(row);
  const fallback = pricing.phases[pricing.phases.length - 1];
  const segments = peaks.reduce((total, item) => total + item.windows.length, 0);
  const scope = peaks.length > 1 ? `${peaks.length} 条规则 · ` : "";
  return `${scope}高峰 ${formatMultiplier(peakMultipliers(peaks), "/")} / 低峰 ×${formatMultiplier(fallback?.multiplier)}`
    + (segments ? ` · ${segments} 段` : "");
}

/** 所有高峰倍率，去重后展示（多规则时可能是 1/0.9 这样的组合） */
function peakMultipliers(peaks: ReturnType<typeof peakRows>): string {
  const values = peaks.map((item) => String(toNumberOrNull(item.multiplier) ?? "-"));
  return Array.from(new Set(values)).join("/");
}

/** 悬浮明细：每条高峰规则的星期 + 时段，多规则逐个列出 */
function peakTitle(row: Row): string {
  const pricing = peakPricingOf(row);
  if (!pricing) {
    return "";
  }
  const peaks = peakRows(row);
  if (!peaks.length) {
    return "兜底低峰";
  }
  return peaks
    .map((peak, index) => {
      const weekdays = peak.weekdays.length ? peak.weekdays.map(shortWeekday).join("") : "未选星期";
      return `规则 ${index + 1}：${weekdays} ${describePeakWindows(peak)}（×${formatMultiplier(peak.multiplier)}）`;
    })
    .join("\n");
}

/** ISO 星期名 -> 中文短名 */
function shortWeekday(code: string): string {
  return PEAK_WEEKDAYS.find((day) => day.code === code)?.short || code;
}

function peakPricingOf(row: Row) {
  if (String(row.pricingPlan || "").trim().toUpperCase() !== "PEAK_OFF_PEAK") {
    return null;
  }
  return parsePeakPricing(parseJsonObject(row.configJson).pricing);
}

function formatMultiplier(value: unknown, separator = ""): string {
  if (typeof value === "string") {
    return value;
  }
  return String(toNumberOrNull(value) ?? "-");
}
</script>
