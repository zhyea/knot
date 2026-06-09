<template>
  <PageSection>
    <div class="model-page">
      <section class="list-page-block">
        <div class="model-filters">
          <div class="model-filter-item model-filter-item--type">
            <span class="model-filter-label">模型类型</span>
            <EnumSelect
              v-model="query.modelTypes"
              category="model_type"
              multiple
              clearable
              class="model-filter-control"
              :select-style="{ width: '100%' }"
            />
          </div>

          <div class="model-filter-item model-filter-item--keyword">
            <span class="model-filter-label">关键字</span>
            <el-input
              v-model="query.keyword"
              class="model-filter-control"
              placeholder="按模型编码、名称、供应商筛选"
              clearable
              @keyup.enter="handleQuery"
            />
          </div>

          <div class="model-filter-actions">
            <el-button type="primary" @click="handleQuery">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </div>
        </div>
      </section>

      <section class="model-block model-block--table">
        <div class="model-toolbar">
          <el-button type="primary" @click="openCreate">新增模型</el-button>
        </div>

        <ModelListPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :show-refresh="false"
          @edit="openEdit"
          @copy="openCopy"
          @log="openChangeLog"
          @page-change="onPageChange"
          @size-change="onSizeChange"
          @changed="load"
        />
      </section>
    </div>

    <ModelFormDrawer v-model="formVisible" :model="editingModel" @saved="resetPage" />
    <OperationLogDrawer
      v-model="logDrawer"
      :title="`模型变更日志 - ${logModelName || ''}`"
      :load-logs="loadModelOperationLogs"
    />
  </PageSection>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import PageSection from "../components/common/PageSection.vue";
import EnumSelect from "../components/common/EnumSelect.vue";
import OperationLogDrawer from "../components/common/OperationLogDrawer.vue";
import ModelFormDrawer from "../components/model/ModelFormDrawer.vue";
import ModelListPanel from "../components/model/ModelListPanel.vue";
import { getModel, listModels } from "../api/models";
import { listModelOperationLogs } from "../api/operationLogs";
import { usePageList } from "../composables/usePageList";

const query = reactive({
  keyword: "",
  modelTypes: []
});

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listModels, { extra: query });

const formVisible = ref(false);
const editingModel = ref(null);
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

async function openCopy(row) {
  const detail = row?.id ? await getModel(row.id) : row;
  editingModel.value = buildModelCopy(detail || row);
  formVisible.value = true;
}

function openChangeLog(row) {
  logModelId.value = row.id;
  logModelName.value = row.name || row.modelCode || `#${row.id}`;
  logDrawer.value = true;
}

function loadModelOperationLogs() {
  return listModelOperationLogs(logModelId.value);
}

function buildModelCopy(source = {}) {
  return {
    ...source,
    id: null,
    modelCode: copyModelCode(source.modelCode),
    name: source.name ? `${source.name} Copy` : "",
    enabled: false,
    apiBindings: Array.isArray(source.apiBindings)
      ? source.apiBindings.map((item) => ({ ...item, id: null }))
      : []
  };
}

function copyModelCode(modelCode) {
  const code = String(modelCode || "").trim();
  return code ? `${code}-copy` : "";
}

function handleQuery() {
  return resetPage();
}

function handleReset() {
  query.keyword = "";
  query.modelTypes = [];
  return resetPage();
}

onMounted(load);
</script>

<style scoped>
.model-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.model-block {
  border: 1px solid var(--knot-border, #dcdfe6);
  padding: 16px;
}

.model-filters {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.model-filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
}

.model-filter-item--type {
  flex: 0 0 420px;
  min-width: 420px;
}

.model-filter-item--keyword {
  flex: 1 1 360px;
  min-width: 320px;
}

.model-filter-label {
  flex: 0 0 auto;
  color: #606266;
  font-size: 13px;
  white-space: nowrap;
}

.model-filter-control {
  width: 100%;
}

.model-filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.model-toolbar {
  margin-bottom: 14px;
}

@media (max-width: 960px) {
  .model-filter-item--type,
  .model-filter-item--keyword {
    flex: 1 1 100%;
    min-width: 0;
  }

  .model-filter-actions {
    width: 100%;
    margin-left: 0;
  }
}
</style>
