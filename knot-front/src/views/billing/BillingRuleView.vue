<template>
  <PageSection>
    <div class="list-page-shell">
      <section class="list-page-block">
        <div class="list-page-filters">
          <div class="list-filter-item list-filter-item--grow">
            <span class="list-filter-label">关键词</span>
            <el-input
              v-model="query.keyword"
              class="list-filter-control--wide"
              placeholder="按编码、名称、供应商、统一模型筛选"
              clearable
              @keyup.enter="handleQuery"
            />
          </div>
          <div class="list-filter-item">
            <span class="list-filter-label">供应商</span>
            <RemoteEntitySelect
              v-model="query.providerId"
              class="list-filter-control--wide"
              :load-function="listProviders"
              :label-function="providerLabel"
              :selected-options="selectedProviderOptions"
              clearable
              placeholder="全部供应商"
            />
          </div>
          <div class="list-filter-item">
            <span class="list-filter-label">统一模型</span>
            <RemoteEntitySelect
              v-model="query.logicalModelId"
              class="list-filter-control--wide"
              :load-function="listLogicalModels"
              :label-function="logicalModelLabel"
              :selected-options="selectedLogicalModelOptions"
              clearable
              placeholder="全部统一模型"
            />
          </div>
          <div class="list-filter-actions">
            <el-button type="primary" @click="handleQuery">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </div>
        </div>
      </section>

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
          @delete="handleDelete"
          @enabled-change="handleEnabledChange"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <BillingRuleFormDialog v-model="ruleDlg" :rule="currentRule" @saved="resetPage" />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`计费规则变更日志 - ${logRuleName || ''}`"
      :load-logs="loadBillingRuleOperationLogs"
    />
  </PageSection>
</template>

<script setup>
import { computed, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import RemoteEntitySelect from "../../components/common/RemoteEntitySelect.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import BillingRuleFormDialog from "../../components/billing/BillingRuleFormDialog.vue";
import BillingRuleListPanel from "../../components/billing/BillingRuleListPanel.vue";
import { useEnabledToggle } from "../../composables/useEnabledToggle";
import { usePageList } from "../../composables/usePageList";
import { deleteBillingRule, listBillingRules, updateBillingRuleStatus } from "../../api/billing";
import { listBillingRuleOperationLogs } from "../../api/operationLogs";
import { listProviders } from "../../api/providers";
import { listLogicalModels } from "../../api/logicalModels";

const query = ref({
  keyword: "",
  providerId: null,
  logicalModelId: null
});

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listBillingRules, { extra: query.value });

const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateBillingRuleStatus
});

const ruleDlg = ref(false);
const currentRule = ref(null);
const logDrawer = ref(false);
const logRuleId = ref(null);
const logRuleName = ref("");

const selectedProviderOptions = computed(() =>
  rows.value
    .filter((row) => row.providerId === query.value.providerId && row.providerId != null)
    .map((row) => ({ id: row.providerId, name: row.providerName }))
);

const selectedLogicalModelOptions = computed(() =>
  rows.value
    .filter((row) => row.logicalModelId === query.value.logicalModelId && row.logicalModelId != null)
    .map((row) => ({
      id: row.logicalModelId,
      modelName: row.logicalModelName,
      modelCode: row.logicalModelCode
    }))
);

function providerLabel(row) {
  return row?.name || row?.code || `#${row?.id ?? ""}`;
}

function logicalModelLabel(row) {
  return row?.modelName || row?.displayName || row?.modelCode || `#${row?.id ?? ""}`;
}

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

function handleQuery() {
  return resetPage();
}

function handleReset() {
  query.value.keyword = "";
  query.value.providerId = null;
  query.value.logicalModelId = null;
  return resetPage();
}

load();
</script>
