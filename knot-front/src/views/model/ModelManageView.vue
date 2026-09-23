<template>
  <PageSection>
    <div class="model-page">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <FilterField label="模型类型" :width="260">
          <EnumSelect
            v-model="query.modelTypes"
            category="model_type"
            multiple
            clearable
            :select-style="{ width: '100%' }"
          />
        </FilterField>
        <KeywordInput
          v-model="query.keyword"
          placeholder="按模型编码、名称、供应商筛选"
          @query="handleQuery"
        />
      </FilterBar>

      <section class="list-page-block list-page-block--content">
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
import { onMounted, ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import EnumSelect from "../../components/common/EnumSelect.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import ModelFormDrawer from "../../components/model/ModelFormDrawer.vue";
import ModelListPanel from "../../components/model/ModelListPanel.vue";
import { getModel, listModels } from "../../api/models";
import { listModelOperationLogs } from "../../api/operationLogs";
import { useListQuery } from "../../composables/useListQuery";

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
} = useListQuery({ apiFn: listModels, fields: { keyword: "", modelTypes: [] } });

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

onMounted(load);
</script>

<style scoped>
.model-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
