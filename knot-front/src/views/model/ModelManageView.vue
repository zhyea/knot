<template>
  <PageSection>
    <div class="model-page">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <FilterField label="模型类型" :width="260">
          <EnumControl
            v-model="query.modelTypes"
            enum-name="ModelTypeEnum"
            multiple
            clearable
            :select-style="{ width: '100%' }"
          />
        </FilterField>
        <KeywordInput
          v-model="query.keyword"
          placeholder="按模型编码、供应商筛选"
          @query="handleQuery"
        />
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新增模型</el-button>
          </div>
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
          @discount="openDiscount"
          @delete="remove"
          @restore="restore"
          @log="openChangeLog"
          @page-change="onPageChange"
          @size-change="onSizeChange"
          @changed="load"
        />
      </section>
    </div>

    <ModelFormDrawer v-model="formVisible" :model="editingModel" @saved="resetPage" />
    <ModelDiscountDrawer v-model="discountDrawerVisible" :model-code="discountModelCode" />
    <OperationLogDrawer
      v-model="logDrawer"
      :title="`模型变更日志 - ${logModelName || ''}`"
      :load-logs="loadModelOperationLogs"
    />
  </PageSection>
</template>

<script setup lang="ts">
import type {Row} from "@/types";
import {onMounted, ref} from "vue";
import PageSection from "../../components/common/PageSection.vue";
import EnumControl from "../../components/common/EnumControl.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import ModelFormDrawer from "../../components/model/ModelFormDrawer.vue";
import ModelDiscountDrawer from "../../components/model/ModelDiscountDrawer.vue";
import ModelListPanel from "../../components/model/ModelListPanel.vue";
import {deleteModel, getModel, listModels, restoreModel} from "@/api/models";
import {listModelOperationLogs} from "@/api/operationLogs";
import {useListQuery} from "@/composables/useListQuery";
import type {Dict} from "@/types";
import {ElMessage, ElMessageBox} from "element-plus";

/**
 * 管理列表带 includeDeleted=true：已逻辑删除的供应商模型仍展示（浅红底 + 恢复按钮），
 * 排序由后端 is_deleted asc 放到末尾。路由规则 / options 等下拉场景不传该参数，已删除项不列为备选。
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
  apiFn: (params: Dict) => listModels({...params, includeDeleted: true}),
  fields: { keyword: "", modelTypes: [] }
});

const formVisible = ref(false);
const editingModel = ref<Dict | null>(null);
const discountDrawerVisible = ref(false);
const discountModelCode = ref("");
const logDrawer = ref(false);
const logModelId = ref<number | string | null>(null);
const logModelName = ref("");

function openCreate() {
  editingModel.value = null;
  formVisible.value = true;
}

function openEdit(row: Row) {
  editingModel.value = row;
  formVisible.value = true;
}

async function openCopy(row: Row) {
  const detail = row?.id ? await getModel(row.id) : row;
  editingModel.value = buildModelCopy(detail || row);
  formVisible.value = true;
}

/** 折扣策略绑定在模型业务码 model_code 上，故抽屉按 modelCode 而非 id 传参 */
function openDiscount(row: Row) {
  discountModelCode.value = String(row.modelCode || "");
  discountDrawerVisible.value = true;
}

function openChangeLog(row: Row) {
  logModelId.value = row.id;
  logModelName.value = row.modelCode || `#${row.id}`;
  logDrawer.value = true;
}

function loadModelOperationLogs() {
  return listModelOperationLogs(logModelId.value!);
}

function buildModelCopy(source: Dict = {}): Dict {
  return {
    ...source,
    id: null,
    modelCode: copyModelCode(source.modelCode),
    enabled: false,
    apiBindings: Array.isArray(source.apiBindings)
      ? source.apiBindings.map((item) => ({ ...item, id: null }))
      : []
  };
}

function copyModelCode(modelCode: string) {
  const code = String(modelCode || "").trim();
  return code ? `${code}-copy` : "";
}

/** 逻辑删除（不物理删除）；被路由规则引用时后端返回 409，错误提示由 http 层统一弹出 */
async function remove(row: Row) {
  await ElMessageBox.confirm(`确认删除供应商模型“${row.modelCode}”？`, "删除确认", {
    type: "warning"
  });
  await deleteModel(row.id);
  ElMessage.success("已删除");
  resetPage();
}

/** 恢复已逻辑删除的模型：model_code 唯一性按物理行判定，删除后同 code 无法新建，只能恢复 */
async function restore(row: Row) {
  await restoreModel(row.id);
  ElMessage.success("已恢复");
  resetPage();
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
