<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按供应商、统一模型筛选"
          @query="handleQuery"
        />
        <FilterField label="供应商" :width="220">
          <RemoteEntitySelect
            v-model="query.providerCode"
            value-key="code"
            :load-function="listProviderProfiles"
            :label-function="providerLabel"
            :selected-options="selectedProviderOptions"
            clearable
            placeholder="全部供应商"
          />
        </FilterField>
        <FilterField label="统一模型" :width="220">
          <RemoteEntitySelect
            v-model="query.logicalModelCode"
            value-key="modelCode"
            :load-function="listLogicalModels"
            :label-function="logicalModelLabel"
            :selected-options="selectedLogicalModelOptions"
            clearable
            placeholder="全部统一模型"
          />
        </FilterField>
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建规则</el-button>
          </div>
        </div>

        <BillingRuleListPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :toggling-id="togglingId"
          :show-refresh="false"
          @edit="openEdit"
          @log="openChangeLog"
          @tier-detail="openTierDetail"
          @peak-detail="openPeakDetail"
          @preview="openPreview"
          @delete="handleDelete"
          @enabled-change="handleEnabledChange"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <BillingRuleFormDialog v-model="ruleDlg" :rule="currentRule" @saved="resetPage" />

    <TierRuleDetailDrawer v-model="tierDrawer" :rule="tierRule" />

    <PeakRuleDetailDrawer v-model="peakDrawer" :rule="peakRule" />

    <PricingPreviewDrawer v-model="previewDrawer" :rule="previewRule" />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`计费规则变更日志 - ${logRuleName || ''}`"
      :load-logs="loadBillingRuleOperationLogs"
    />
  </PageSection>
</template>

<script setup lang="ts">
import type {Dict, Row} from "@/types";
import {computed, ref} from "vue";
import {ElMessage} from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import RemoteEntitySelect from "../../components/common/RemoteEntitySelect.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import BillingRuleFormDialog from "../../components/billing/BillingRuleFormDialog.vue";
import BillingRuleListPanel from "../../components/billing/BillingRuleListPanel.vue";
import TierRuleDetailDrawer from "../../components/billing/TierRuleDetailDrawer.vue";
import PeakRuleDetailDrawer from "../../components/billing/PeakRuleDetailDrawer.vue";
import PricingPreviewDrawer from "../../components/billing/PricingPreviewDrawer.vue";
import {useEnabledToggle} from "@/composables/useEnabledToggle";
import {useListQuery} from "@/composables/useListQuery";
import {deleteBillingRule, listBillingRules, updateBillingRuleStatus} from "@/api/billing";
import {listBillingRuleOperationLogs} from "@/api/operationLogs";
import {listProviderProfiles} from "@/api/providerProfiles";
import {listLogicalModels} from "@/api/logicalModels";

const {
  query,
  rows,
  loading,
  total,
  pageNum,
  pageSize,
  load,
  onPageChange,
  onSizeChange,
  resetPage,
  handleQuery,
  handleReset
} = useListQuery({ apiFn: listBillingRules, fields: { keyword: "", providerCode: null, logicalModelCode: null } });

const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateBillingRuleStatus
});

const ruleDlg = ref(false);
const currentRule = ref<Dict | null>(null);
const logDrawer = ref(false);
const logRuleId = ref<number | string | null>(null);
const logRuleName = ref("");
const tierDrawer = ref(false);
const tierRule = ref<Row | null>(null);
const peakDrawer = ref(false);
const peakRule = ref<Row | null>(null);
const previewDrawer = ref(false);
const previewRule = ref<Row | null>(null);

const selectedProviderOptions = computed(() =>
  rows.value
    .filter((row) => row.providerCode === query.providerCode && row.providerCode != null)
    .map((row) => ({ id: row.providerCode, name: row.providerName }))
);

const selectedLogicalModelOptions = computed(() =>
  rows.value
    .filter((row) => row.logicalModelCode === query.logicalModelCode && row.logicalModelCode != null)
    .map((row) => ({
      id: row.logicalModelCode,
      modelName: row.logicalModelName,
      modelCode: row.logicalModelCode
    }))
);

function providerLabel(row: Row) {
  return row?.name || row?.code || `#${row?.id ?? ""}`;
}

function logicalModelLabel(row: Row) {
  return row?.modelName || row?.displayName || row?.modelCode || `#${row?.id ?? ""}`;
}

function openCreate() {
  currentRule.value = null;
  ruleDlg.value = true;
}

function openEdit(row: Row) {
  currentRule.value = row;
  ruleDlg.value = true;
}

function openChangeLog(row: Row) {
  logRuleId.value = row.id;
  logRuleName.value = row.code || `#${row.id}`;
  logDrawer.value = true;
}

function openTierDetail(row: Row) {
  tierRule.value = row;
  tierDrawer.value = true;
}

function openPeakDetail(row: Row) {
  peakRule.value = row;
  peakDrawer.value = true;
}

function openPreview(row: Row) {
  previewRule.value = row;
  previewDrawer.value = true;
}

function loadBillingRuleOperationLogs() {
  return listBillingRuleOperationLogs(logRuleId.value!);
}

async function handleEnabledChange(row: Row, enabled: string | number | boolean) {
  await onEnabledChange(row, enabled);
  await load();
}

async function handleDelete(row: Row) {
  await deleteBillingRule(row.id);
  ElMessage.success("已删除");
  await load();
}


load();
</script>
