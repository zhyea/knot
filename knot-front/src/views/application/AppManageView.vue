<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按 App ID、名称、部门、负责人筛选"
          @query="handleQuery"
        />
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建应用</el-button>
          </div>
        </div>

        <AppListPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :show-refresh="false"
          @edit="openEdit"
          @log="openChangeLog"
          @page-change="onPageChange"
          @size-change="onSizeChange"
          @changed="load"
        />
      </section>
    </div>

    <AppFormDrawer v-model="formVisible" :app="editingApp" @saved="onAppSaved" />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`应用变更日志 - ${logAppName || ''}`"
      :load-logs="loadAppOperationLogs"
    />
  </PageSection>
</template>

<script setup>
import { onMounted, ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import AppListPanel from "../../components/app/AppListPanel.vue";
import AppFormDrawer from "../../components/app/AppFormDrawer.vue";
import { listAppOperationLogs } from "../../api/operationLogs";
import { useListQuery } from "../../composables/useListQuery";
import { listApps } from "../../api/apps";

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
} = useListQuery({ apiFn: listApps, fields: { keyword: "" } });

const formVisible = ref(false);
const editingApp = ref(null);
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

function openChangeLog(row) {
  logAppId.value = row.id;
  logAppName.value = row.name || row.appId || `#${row.id}`;
  logDrawer.value = true;
}

function loadAppOperationLogs() {
  return listAppOperationLogs(logAppId.value);
}

onMounted(load);
</script>
