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
              placeholder="按 App ID、名称、部门、负责人筛选"
              clearable
              @keyup.enter="handleQuery"
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
import { onMounted, reactive, ref } from "vue";
import PageSection from "../components/common/PageSection.vue";
import OperationLogDrawer from "../components/common/OperationLogDrawer.vue";
import AppListPanel from "../components/app/AppListPanel.vue";
import AppFormDrawer from "../components/app/AppFormDrawer.vue";
import { listAppOperationLogs } from "../api/operationLogs";
import { usePageList } from "../composables/usePageList";
import { listApps } from "../api/apps";

const query = reactive({
  keyword: ""
});

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listApps, { extra: query });

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

function handleQuery() {
  return resetPage();
}

function handleReset() {
  query.keyword = "";
  return resetPage();
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
