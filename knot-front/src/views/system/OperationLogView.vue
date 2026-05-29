<template>
  <PageSection title="操作日志">
    <OperationLogListPanel
      :query="queryForm"
      :rows="rows"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      :status-label="statusLabel"
      @query="handleQuery"
      @reset="handleReset"
      @refresh="load"
      @row-click="onLogRow"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    />

    <OperationLogDetailDrawer
      v-model="detailDrawer"
      :log="currentLog"
      :status-label="statusLabel"
    />
  </PageSection>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import OperationLogDetailDrawer from "../../components/system/OperationLogDetailDrawer.vue";
import OperationLogListPanel from "../../components/system/OperationLogListPanel.vue";
import { usePageList } from "../../composables/usePageList";
import { resolveEnumLabel, useEnums } from "../../composables/useEnums";
import { getOperationLogDetail, listOperationLogs } from "../../api/operationLogs";

const { options: statusOptions, loadOptions: loadStatusOptions } = useEnums("status");

function statusLabel(code) {
  return resolveEnumLabel(statusOptions.value, code, code || "—");
}

onMounted(() => loadStatusOptions());

const queryForm = reactive({
  module: "",
  operation: "",
  status: ""
});

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange } = usePageList(
  listOperationLogs,
  { extra: queryForm }
);
const detailDrawer = ref(false);
const currentLog = ref(null);

function handleQuery() {
  load();
}

function handleReset() {
  queryForm.module = "";
  queryForm.operation = "";
  queryForm.status = "";
  load();
}

async function onLogRow(row) {
  currentLog.value = await getOperationLogDetail(row.id);
  detailDrawer.value = true;
}

load();
</script>
