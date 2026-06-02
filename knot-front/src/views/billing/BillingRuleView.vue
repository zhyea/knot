<template>
  <PageSection title="计费规则">
    <BillingRuleListPanel
      :rows="rows"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      :toggling-id="togglingId"
      @create="openCreate"
      @edit="openEdit"
      @log="openChangeLog"
      @delete="handleDelete"
      @refresh="load"
      @enabled-change="handleEnabledChange"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    />

    <BillingRuleFormDialog v-model="ruleDlg" :rule="currentRule" @saved="resetPage" />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`计费规则变更日志 — ${logRuleName || ''}`"
      :load-logs="loadBillingRuleOperationLogs"
    />
  </PageSection>
</template>

<script setup>
import { ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import BillingRuleFormDialog from "../../components/billing/BillingRuleFormDialog.vue";
import BillingRuleListPanel from "../../components/billing/BillingRuleListPanel.vue";
import { useEnabledToggle } from "../../composables/useEnabledToggle";
import { usePageList } from "../../composables/usePageList";
import { deleteBillingRule, listBillingRules, updateBillingRule } from "../../api/billing";
import { listBillingRuleOperationLogs } from "../../api/operationLogs";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listBillingRules);

const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateBillingRule,
  buildPayload: (row, enabled) => ({
    code: row.code,
    name: row.name,
    providerId: row.providerId ?? null,
    logicalModelId: row.logicalModelId ?? null,
    billingMode: row.billingMode,
    currency: row.currency,
    itemType: row.itemType,
    unit: row.unit,
    unitPrice: row.unitPrice,
    configJson: row.configJson ?? null,
    ladderJson: row.ladderJson ?? null,
    enabled,
    remark: row.remark ?? null
  })
});

const ruleDlg = ref(false);
const currentRule = ref(null);
const logDrawer = ref(false);
const logRuleId = ref(null);
const logRuleName = ref("");

function openCreate() {
  currentRule.value = null;
  ruleDlg.value = true;
}

function openEdit(row) {
  currentRule.value = row;
  ruleDlg.value = true;
}

function openChangeLog(row) {
  logRuleId.value = row.id;
  logRuleName.value = row.name || `#${row.id}`;
  logDrawer.value = true;
}

function loadBillingRuleOperationLogs() {
  return listBillingRuleOperationLogs(logRuleId.value);
}

async function handleEnabledChange(row, enabled) {
  await onEnabledChange(row, enabled);
  await load();
}

async function handleDelete(row) {
  await deleteBillingRule(row.id);
  ElMessage.success("已删除");
  await load();
}

load();
</script>
