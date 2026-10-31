<template>
  <DashboardPanel
    title="资源概览"
    subtitle="按当前系统资源数量和模块能力密度汇总。"
    :badge="`模块 ${formatThousands(moduleCount)}`"
    overview
  >
    <div class="overview-layout">
      <div class="overview-chart">
        <div v-for="metric in metrics" :key="metric.key" class="overview-row">
          <div class="overview-row__head">
            <span class="overview-row__label">{{ metric.label }}</span>
            <strong class="overview-row__value">{{ formatThousands(metric.value) }}</strong>
          </div>
          <div class="overview-row__track">
            <span
              class="overview-row__fill"
              :class="{
                'overview-row__fill--teal': metric.tone === 'teal',
                'overview-row__fill--green': metric.tone === 'green',
                'overview-row__fill--orange': metric.tone === 'orange',
                'overview-row__fill--blue': metric.tone === 'blue'
              }"
              :style="{ width: `${metric.ratio}%` }"
            />
          </div>
          <div class="overview-row__meta">{{ metric.helperText }}</div>
        </div>
      </div>

      <div class="module-overview">
        <div v-for="module in modules" :key="module.code" class="module-overview__item">
          <div class="module-overview__head">
            <div>
              <div class="module-overview__name">{{ module.name }}</div>
              <div class="module-overview__code">{{ module.code }}</div>
            </div>
            <span class="module-overview__count">{{ module.capabilityCount }} 项能力</span>
          </div>
          <div class="module-overview__caps">
            <el-tag v-for="capability in module.capabilityPreview" :key="capability" size="small" effect="plain">
              {{ capability }}
            </el-tag>
            <span v-if="module.remainingCapabilityCount > 0" class="module-overview__more">
              +{{ module.remainingCapabilityCount }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </DashboardPanel>
</template>

<script setup lang="ts">
import type {PropType} from "vue";
import {formatThousands} from "@/utils/format";
import type {MetricItem} from "@/utils/dashboardMetrics";
import type {RankedModule} from "@/utils/dashboardModules";
import DashboardPanel from "./DashboardPanel.vue";

defineProps({
  metrics: {type: Array as PropType<MetricItem[]>, required: true},
  /** 已按能力密度排好序的模块（排序在 utils/dashboardModules） */
  modules: {type: Array as PropType<RankedModule[]>, required: true},
  /** 模块总数（含未进前 6 的），与 modules.length 不是一个概念 */
  moduleCount: {type: Number, required: true}
});
</script>

<style scoped>
.overview-layout {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(320px, 100%), 1fr));
  gap: 24px;
  align-items: stretch;
  flex: 1 1 auto;
  min-height: 0;
}

.overview-chart {
  display: flex;
  flex-direction: column;
  gap: clamp(18px, 3vh, 44px);
  min-height: 0;
  padding-right: 8px;
  overflow: auto;
}

.overview-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.overview-row__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.overview-row__label {
  font-size: var(--el-font-size-base, 13px);
  color: #606266;
}

.overview-row__value {
  font-size: 18px;
  color: #1f2937;
}

.overview-row__track {
  position: relative;
  height: 12px;
  overflow: hidden;
  background: #eef3f8;
}

.overview-row__fill {
  display: block;
  height: 100%;
}

/* 与指标卡共用同一套色调（原来是同一条规则里的并列选择器，拆成两个组件后各自持有一份） */
.overview-row__fill--teal {
  background: #23b3c9;
}

.overview-row__fill--green {
  background: #56b881;
}

.overview-row__fill--orange {
  background: #f59e0b;
}

.overview-row__fill--blue {
  background: #4a94e8;
}

.overview-row__meta {
  font-size: 12px;
  color: #909399;
}

.module-overview {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  overflow: auto;
}

.module-overview__item {
  padding: 14px;
  border: 1px solid #edf2f7;
  background: #fbfcfe;
}

.module-overview__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.module-overview__name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.module-overview__code {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.module-overview__count {
  font-size: 12px;
  color: #4b88d8;
}

.module-overview__caps {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.module-overview__more {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  color: #909399;
}

@media (max-width: 768px) {
  .overview-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
