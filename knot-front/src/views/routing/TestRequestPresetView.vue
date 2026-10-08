<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按编码、名称、备注筛选"
          @query="handleQuery"
        />
        <FilterField label="协议" :width="260">
          <EnumControl
            v-model="query.protocol"
            enum-name="ModelApiProtocolEnum"
            clearable
            filterable
            placeholder="按协议筛选"
          />
        </FilterField>
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建预设</el-button>
          </div>
        </div>

        <TestRequestPresetListPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :toggling-id="togglingId"
          :show-refresh="false"
          @action="handleAction"
          @enabled-change="handleEnabledChange"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <TestRequestPresetFormDrawer
      v-model="formVisible"
      :preset="editingPreset"
      :readonly="viewMode"
      @saved="resetPage"
    />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`预设请求操作记录 - ${logPresetName || ''}`"
      :load-logs="loadTestRequestPresetOperationLogs"
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
import EnumControl from "../../components/common/EnumControl.vue";
import TestRequestPresetListPanel from "../../components/routing/TestRequestPresetListPanel.vue";
import TestRequestPresetFormDrawer from "../../components/routing/TestRequestPresetFormDrawer.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import {useListQuery} from "@/composables/useListQuery";
import {
  deleteTestRequestPreset,
  listTestRequestPresets,
  updateTestRequestPresetStatus
} from "@/api/routing";
import {listTestRequestPresetOperationLogs} from "@/api/operationLogs";

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
} = useListQuery({ apiFn: listTestRequestPresets, fields: { keyword: "", protocol: "" } });

const formVisible = ref(false);
const editingPreset = ref<Dict | null>(null);
const viewMode = ref(false);
const togglingId = ref<number | string | null>(null);

function openCreate() {
  editingPreset.value = null;
  viewMode.value = false;
  formVisible.value = true;
}

function openEdit(row: Row) {
  editingPreset.value = row;
  viewMode.value = false;
  formVisible.value = true;
}

function openView(row: Row) {
  editingPreset.value = row;
  viewMode.value = true;
  formVisible.value = true;
}

function handleAction(action: string, row: Row) {
  if (action === "view") {
    openView(row);
  } else if (action === "edit") {
    openEdit(row);
  } else if (action === "log") {
    openLog(row);
  } else if (action === "delete") {
    removePreset(row);
  }
}

const logDrawer = ref(false);
const logPresetId = ref<number | string | null>(null);
const logPresetName = ref("");

function openLog(row: Row) {
  logPresetId.value = row.id;
  logPresetName.value = row.name || row.code || `#${row.id}`;
  logDrawer.value = true;
}

function loadTestRequestPresetOperationLogs() {
  return listTestRequestPresetOperationLogs(logPresetId.value!);
}

async function removePreset(row: Row) {
  try {
    await deleteTestRequestPreset(row.id);
    ElMessage.success("已删除");
    load();
  } catch {
    // 错误提示由响应拦截器处理
  }
}

async function handleEnabledChange(row: Row, enabled: boolean | string | number) {
  const flag = enabled !== false && enabled !== 0 && enabled !== "false";
  const prevStatus = row.status;
  // 数字状态：1-启用 0-停用；0 是合法值，不能写 truthy 判断
  if (flag === (prevStatus === 1)) {
    return;
  }
  const nextStatus = flag ? 1 : 0;
  row.status = nextStatus;
  togglingId.value = row.id;
  try {
    await updateTestRequestPresetStatus(row.id, flag);
    ElMessage.success(flag ? "已启用" : "已禁用");
  } catch {
    row.status = prevStatus;
  } finally {
    togglingId.value = null;
  }
}

load();
</script>
