<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按模型池编码、名称筛选"
          @query="handleQuery"
        />
        <FilterField label="模型类型" :width="260">
          <EnumControl
            v-model="query.modelTypes"
            enum-name="ModelTypeEnum"
            multiple
            collapse-tags
            collapse-tags-tooltip
            clearable
          />
        </FilterField>
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建模型池</el-button>
          </div>
        </div>

        <ModelPoolListPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :show-refresh="false"
          @edit="openEdit"
          @delete="remove"
          @log="openChangeLog"
          @page-change="onPageChange"
          @size-change="onSizeChange"
          @changed="load"
        />
      </section>
    </div>

    <ModelPoolFormDrawer v-model="formVisible" :pool="editingPool" @saved="resetPage" />
    <OperationLogDrawer
      v-model="logDrawer"
      :title="`模型池变更日志 - ${logPoolName || ''}`"
      :load-logs="loadModelPoolOperationLogs"
    />
  </PageSection>
</template>

<script setup lang="ts">
import type {Dict, Row} from "@/types";
import {onMounted, ref} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import {useListQuery} from "@/composables/useListQuery";
import EnumControl from "../../components/common/EnumControl.vue";
import ModelPoolListPanel from "../../components/model/ModelPoolListPanel.vue";
import ModelPoolFormDrawer from "../../components/model/ModelPoolFormDrawer.vue";
import {deleteModelPool, listModelPools, restoreModelPool} from "@/api/modelPools";
import {listModelPoolOperationLogs} from "@/api/operationLogs";

/**
 * 管理列表带 includeDeleted=true：已逻辑删除的模型池仍展示（浅红底 + 恢复按钮），
 * 排序由后端 is_deleted asc 放到末尾。路由规则等下拉场景不传该参数，已删除项不列为备选。
 */
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
} = useListQuery({
  apiFn: (params: Dict) => listModelPools({...params, includeDeleted: true}),
  fields: { keyword: "", modelTypes: [] }
});

const formVisible = ref(false);
const editingPool = ref<Dict | null>(null);
const logDrawer = ref(false);
const logPoolId = ref<number | string | null>(null);
const logPoolName = ref("");

function openCreate() {
  editingPool.value = null;
  formVisible.value = true;
}

function openEdit(row: Row) {
  editingPool.value = row;
  formVisible.value = true;
}

async function remove(row: Row) {
  await ElMessageBox.confirm(`确认删除模型池“${row.name || row.poolCode}”？`, "删除确认", {
    type: "warning"
  });
  await deleteModelPool(row.id);
  ElMessage.success("已删除");
  resetPage();
}

async function restore(row: Row) {
  await restoreModelPool(row.id);
  ElMessage.success("已恢复");
  resetPage();
}

function openChangeLog(row: Row) {
  logPoolId.value = row.id;
  logPoolName.value = row.poolCode || `#${row.id}`;
  logDrawer.value = true;
}

function loadModelPoolOperationLogs() {
  return listModelPoolOperationLogs(logPoolId.value!);
}

onMounted(load);
</script>
