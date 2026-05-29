<template>
  <PageSection title="供应商模型">
    <ModelListPanel
      :rows="rows"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      @create="openCreate"
      @refresh="load"
      @edit="openEdit"
      @test="openTest"
      @switch="openSwitch"
      @log="openChangeLog"
      @page-change="onPageChange"
      @size-change="onSizeChange"
      @changed="load"
    />

    <ModelFormDrawer v-model="formVisible" :model="editingModel" @saved="resetPage" />
    <ModelTestDialog v-model="testDlg" :model-id="testingModelId" />
    <ModelVersionSwitchDialog v-model="switchDlg" :model="switchingModel" @switched="resetPage" />
    <OperationLogDrawer
      v-model="logDrawer"
      :title="`模型变更日志 — ${logModelName || ''}`"
      :load-logs="loadModelOperationLogs"
    />
  </PageSection>
</template>

<script setup>
import { onMounted, ref } from "vue";
import PageSection from "../components/common/PageSection.vue";
import OperationLogDrawer from "../components/common/OperationLogDrawer.vue";
import ModelFormDrawer from "../components/model/ModelFormDrawer.vue";
import ModelListPanel from "../components/model/ModelListPanel.vue";
import ModelTestDialog from "../components/model/ModelTestDialog.vue";
import ModelVersionSwitchDialog from "../components/model/ModelVersionSwitchDialog.vue";
import { listModels } from "../api/models";
import { listModelOperationLogs } from "../api/operationLogs";
import { usePageList } from "../composables/usePageList";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } = usePageList(listModels);

const formVisible = ref(false);
const editingModel = ref(null);
const testDlg = ref(false);
const testingModelId = ref(null);
const switchDlg = ref(false);
const switchingModel = ref(null);
const logDrawer = ref(false);
const logModelId = ref(null);
const logModelName = ref("");

function openCreate() {
  editingModel.value = null;
  formVisible.value = true;
}

function openEdit(row) {
  editingModel.value = row;
  formVisible.value = true;
}

function openTest(row) {
  testingModelId.value = row.id;
  testDlg.value = true;
}

function openSwitch(row) {
  switchingModel.value = row;
  switchDlg.value = true;
}

function openChangeLog(row) {
  logModelId.value = row.id;
  logModelName.value = row.name || row.modelCode || `#${row.id}`;
  logDrawer.value = true;
}

function loadModelOperationLogs() {
  return listModelOperationLogs(logModelId.value);
}

onMounted(() => {
  load();
});
</script>
