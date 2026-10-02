<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按规则编码、模型族筛选"
          @query="handleQuery"
        />
        <FilterField label="模型族" :width="220">
          <EnumSelect
            v-model="query.modelFamilyCode"
            category="model_family"
            clearable
            placeholder="全部模型族"
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
          @preview="openPreview"
          @delete="handleDelete"
          @enabled-change="handleEnabledChange"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <BillingRuleFormDialog v-model="ruleDlg" :rule="currentRule" @saved="resetPage" />

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
import {ref} from "vue";
import {ElMessage} from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import EnumSelect from "../../components/common/EnumSelect.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import BillingRuleFormDialog from "../../components/billing/BillingRuleFormDialog.vue";
import BillingRuleListPanel from "../../components/billing/BillingRuleListPanel.vue";
import PricingPreviewDrawer from "../../components/billing/PricingPreviewDrawer.vue";
import {useEnabledToggle} from "@/composables/useEnabledToggle";
import {useListQuery} from "@/composables/useListQuery";
import {deleteBillingRule, listBillingRules, updateBillingRuleStatus} from "@/api/billing";
import {listBillingRuleOperationLogs} from "@/api/operationLogs";

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
} = useListQuery({ apiFn: listBillingRules, fields: { keyword: "", modelFamilyCode: null } });

const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateBillingRuleStatus
});

const ruleDlg = ref(false);
const currentRule = ref<Dict | null>(null);
const logDrawer = ref(false);
const logRuleId = ref<number | string | null>(null);
const logRuleName = ref("");
const previewDrawer = ref(false);
const previewRule = ref<Row | null>(null);

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
