<template>
  <PageSection>
    <div v-loading="loading" class="report-page">
      <section class="report-stats">
        <article v-for="card in statCards" :key="card.label" class="report-stat">
          <span class="report-stat__label">{{ card.label }}</span>
          <strong class="report-stat__value">{{ card.value }}</strong>
        </article>
      </section>

      <section class="report-grid">
        <div class="report-block">
          <h3 class="report-block__title">供应商分布</h3>
          <el-table :data="summary?.byProvider || []" size="small" border>
            <el-table-column label="供应商" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.providerName || row.providerCode || "（全局）" }}</template>
            </el-table-column>
            <el-table-column label="规则数" width="90" align="center">
              <template #default="{ row }">{{ row.ruleCount }}</template>
            </el-table-column>
            <el-table-column label="启用数" width="90" align="center">
              <template #default="{ row }">{{ row.activeCount }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="report-block">
          <h3 class="report-block__title">计费模式分布</h3>
          <el-table :data="modeRows" size="small" border>
            <el-table-column label="计费模式" min-width="140">
              <template #default="{ row }">{{ resolveEnumLabel(modeOptions, row.code, row.code || "（未配置）") }}</template>
            </el-table-column>
            <el-table-column label="规则数" width="90" align="center">
              <template #default="{ row }">{{ row.count }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="report-block">
          <h3 class="report-block__title">进阶方案分布</h3>
          <el-table :data="planRows" size="small" border>
            <el-table-column label="进阶方案" min-width="140">
              <template #default="{ row }">{{ resolveEnumLabel(planOptions, row.code, row.code || "（未配置）") }}</template>
            </el-table-column>
            <el-table-column label="规则数" width="90" align="center">
              <template #default="{ row }">{{ row.count }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="report-block">
          <h3 class="report-block__title">币种分布</h3>
          <el-table :data="summary?.byCurrency || []" size="small" border>
            <el-table-column label="币种" min-width="140">
              <template #default="{ row }">{{ row.code || "（未配置）" }}</template>
            </el-table-column>
            <el-table-column label="规则数" width="90" align="center">
              <template #default="{ row }">{{ row.count }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </div>
  </PageSection>
</template>

<script setup lang="ts">
import type {Dict, Row} from "@/types";
import {computed, onMounted, ref} from "vue";
import PageSection from "@/components/common/PageSection.vue";
import {getBillingReportSummary} from "@/api/billing";
import {useEnums, resolveEnumLabel} from "@/composables/useEnums";

const loading = ref(false);
const summary = ref<Dict | null>(null);

const {options: modeOptions, loadOptions: loadModeOptions} = useEnums("billing_mode");
const {options: planOptions, loadOptions: loadPlanOptions} = useEnums("billing_pricing_plan");

const statCards = computed(() => [
  {label: "规则总数", value: summary.value?.totalRules ?? 0},
  {label: "启用规则", value: summary.value?.activeRules ?? 0},
  {label: "停用规则", value: summary.value?.inactiveRules ?? 0},
  {label: "含生效版本", value: summary.value?.activeVersionRules ?? 0},
  {label: "涉及供应商", value: summary.value?.providerCount ?? 0}
]);

/** 计费模式分布行（枚举字典加载失败时兜底展示原始 code） */
const modeRows = computed<Row[]>(() => (summary.value?.byBillingMode as Row[]) || []);
const planRows = computed<Row[]>(() => (summary.value?.byPricingPlan as Row[]) || []);

async function loadSummary() {
  loading.value = true;
  try {
    summary.value = await getBillingReportSummary();
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadSummary();
  loadModeOptions();
  loadPlanOptions();
});
</script>

<style scoped>
.report-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 200px;
}

.report-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}

.report-stat {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid var(--knot-border, #ebeef5);
  background: #fbfcfe;
}

.report-stat__label {
  font-size: 13px;
  color: #909399;
}

.report-stat__value {
  font-size: 26px;
  line-height: 1.1;
  font-weight: 700;
  color: #1f2937;
}

.report-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(320px, 100%), 1fr));
  gap: 16px;
}

.report-block {
  border: 1px solid var(--knot-border, #ebeef5);
  padding: 14px 16px;
}

.report-block__title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
</style>
