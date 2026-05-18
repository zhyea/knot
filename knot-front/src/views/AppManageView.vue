<template>
  <PageSection title="应用管理">
    <AppListPanel
      :rows="rows"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      @create="openCreate"
      @refresh="load"
      @edit="openEdit"
      @metrics="openMetrics"
      @log="openChangeLog"
      @page-change="onPageChange"
      @size-change="onSizeChange"
      @changed="load"
    />

    <AppFormDrawer v-model="formVisible" :app="editingApp" @saved="onAppSaved" />

    <AppMetricsDrawer
      v-model="metricsVisible"
      :app-id="metricsAppId"
      :app-name="metricsAppName"
    />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`应用变更日志 — ${logAppName || ''}`"
      :load-logs="loadAppOperationLogs"
    />
  </PageSection>
</template>

<script setup>
import { ref, onMounted } from "vue";
import PageSection from "../components/common/PageSection.vue";
import OperationLogDrawer from "../components/common/OperationLogDrawer.vue";
import AppListPanel from "../components/app/AppListPanel.vue";
import AppFormDrawer from "../components/app/AppFormDrawer.vue";
import AppMetricsDrawer from "../components/app/AppMetricsDrawer.vue";
import { listAppOperationLogs } from "../api/operationLogs";
import { usePageList } from "../composables/usePageList";
import { listApps } from "../api/apps";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listApps);

const formVisible = ref(false);
const editingApp = ref(null);

const metricsVisible = ref(false);
const metricsAppId = ref(null);
const metricsAppName = ref("");

const logDrawer = ref(false);
const logAppId = ref(null);
const logAppName = ref("");

function openCreate() {
  editingApp.value = null;
  formVisible.value = true;
}

function openEdit(row) {
  editingApp.value = row;
  formVisible.value = true;
}

function onAppSaved() {
  resetPage();
}

function openMetrics(row) {
  metricsAppId.value = row.id;
  metricsAppName.value = row.name || row.appId || "";
  metricsVisible.value = true;
}

function openChangeLog(row) {
  logAppId.value = row.id;
  logAppName.value = row.name || row.appId || `#${row.id}`;
  logDrawer.value = true;
}

function loadAppOperationLogs() {
  return listAppOperationLogs(logAppId.value);
}

onMounted(() => {
  load();
});
</script>
