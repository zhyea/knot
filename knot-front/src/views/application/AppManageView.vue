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

<script setup lang="ts">

import {onMounted} from "vue";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import AppListPanel from "../../components/app/AppListPanel.vue";
import AppFormDrawer from "../../components/app/AppFormDrawer.vue";
import {listAppOperationLogs} from "@/api/operationLogs";
import {useListQuery} from "@/composables/useListQuery";
import {listApps} from "@/api/apps";
import {useLogDrawer} from "@/composables/useLogDrawer";
import {useRowFormDrawer} from "@/composables/useFormDrawer";
const {id: logAppId, name: logAppName, visible: logDrawer, open: openChangeLog} = useLogDrawer({labelFields: ["name", "appCode"]});
const {visible: formVisible, editing: editingApp, openCreate, openEdit} = useRowFormDrawer();

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


function onAppSaved() {
  resetPage();
}

function loadAppOperationLogs() {
  return listAppOperationLogs(logAppId.value!);
}

onMounted(load);
</script>
