<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按编码、名称筛选"
          @query="handleQuery"
        />
        <FilterField label="标签" :width="180">
          <el-select v-model="query.tag" clearable placeholder="全部">
            <el-option v-for="item in tagPreset" :key="item" :label="item" :value="item" />
          </el-select>
        </FilterField>
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建供应商信息</el-button>
          </div>
        </div>

        <ProviderProfileListPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          @action="onRowAction"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <ProviderProfileFormDrawer
      v-model="formVisible"
      :provider-profile="editing"
      @saved="load"
    />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`供应商信息变更记录 - ${logName || ''}`"
      :load-logs="loadProfileLogs"
    />
  </PageSection>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import { useListQuery } from "../../composables/useListQuery";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import ProviderProfileListPanel from "../../components/provider/ProviderProfileListPanel.vue";
import ProviderProfileFormDrawer from "../../components/provider/ProviderProfileFormDrawer.vue";
import { deleteProviderProfile, listProviderProfiles } from "../../api/providerProfiles";
import { listProviderProfileOperationLogs } from "../../api/operationLogs";

const tagPreset = ["原厂", "云厂商", "代理"];

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
} = useListQuery({ apiFn: listProviderProfiles, fields: { keyword: "", tag: "" } });

const formVisible = ref(false);
const editing = ref(null);

const logDrawer = ref(false);
const logId = ref(null);
const logName = ref("");

function openCreate() {
  editing.value = null;
  formVisible.value = true;
}

function openEdit(row) {
  editing.value = row;
  formVisible.value = true;
}

async function remove(row) {
  await deleteProviderProfile(row.id);
  ElMessage.success("删除成功");
  await load();
}

function openLog(row) {
  logId.value = row.id;
  logName.value = row.name || `#${row.id}`;
  logDrawer.value = true;
}

function loadProfileLogs() {
  return listProviderProfileOperationLogs(logId.value);
}

function onRowAction(key, row) {
  if (key === "edit") {
    openEdit(row);
  } else if (key === "log") {
    openLog(row);
  } else if (key === "delete") {
    remove(row);
  }
}


onMounted(load);
</script>

