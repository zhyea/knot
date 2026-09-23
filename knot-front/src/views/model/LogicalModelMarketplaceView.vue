<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按模型编码、名称、模型族筛选"
          @query="handleQuery"
        />
        <FilterField label="模型类型" :width="260">
          <EnumSelect
            v-model="query.modelTypes"
            category="model_type"
            multiple
            collapse-tags
            collapse-tags-tooltip
            clearable
          />
        </FilterField>
      </FilterBar>

      <section class="list-page-block">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建统一模型</el-button>
          </div>
          <div class="list-page-toolbar__meta market-toolbar__meta">
            <div class="list-page-toolbar__title">
              统一对外模型入口，供应商真实模型通过映射维护。
            </div>
            <el-segmented
              v-model="viewMode"
              :options="viewModeOptions"
              size="small"
              @change="onViewModeChange"
            />
          </div>
        </div>

        <LogicalModelTable
          v-if="viewMode === 'list'"
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :page-sizes="viewPageSizes"
          :model-type-options="modelTypeOptions"
          :show-refresh="false"
          @action="handleAction"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
        <LogicalModelCardGrid
          v-else
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :page-sizes="viewPageSizes"
          :model-type-options="modelTypeOptions"
          :show-refresh="false"
          @action="handleAction"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <LogicalModelFormDrawer v-model="formVisible" :model="editingModel" @saved="resetPage" />
  </PageSection>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import { useListQuery } from "../../composables/useListQuery";
import EnumSelect from "../../components/common/EnumSelect.vue";
import LogicalModelFormDrawer from "../../components/model/LogicalModelFormDrawer.vue";
import LogicalModelTable from "../../components/model/LogicalModelTable.vue";
import LogicalModelCardGrid from "../../components/model/LogicalModelCardGrid.vue";
import { deleteLogicalModel, listLogicalModels } from "../../api/logicalModels";
import { useEnums } from "../../composables/useEnums";
import { getStorageItem, getStorageJson, setStorageItem, setStorageJson } from "../../utils/storage";

const VIEW_MODE_KEY = "knot.logical-model.view-mode";
const VIEW_PAGE_SIZE_KEY = "knot.logical-model.view-page-size";
const DEFAULT_VIEW_PAGE_SIZE = { card: 12, list: 20 };
const VIEW_PAGE_SIZES = { card: [12, 24, 48], list: [10, 20, 50] };

const viewModeOptions = [
  { label: "卡片", value: "card" },
  { label: "列表", value: "list" }
];

function readViewMode() {
  const saved = getStorageItem(VIEW_MODE_KEY);
  return saved === "card" || saved === "list" ? saved : "list";
}

function readViewPageSize() {
  const saved = getStorageJson(VIEW_PAGE_SIZE_KEY, null) || {};
  const result = { ...DEFAULT_VIEW_PAGE_SIZE };
  ["card", "list"].forEach((mode) => {
    const size = Number(saved[mode]);
    if (Number.isInteger(size) && size > 0) {
      result[mode] = size;
    }
  });
  return result;
}

const viewMode = ref(readViewMode());
const viewPageSize = reactive(readViewPageSize());
const viewPageSizes = computed(() => VIEW_PAGE_SIZES[viewMode.value] || VIEW_PAGE_SIZES.list);

const {
  query,
  rows,
  loading,
  total,
  pageNum,
  pageSize,
  load,
  onPageChange,
  resetPage,
  handleQuery,
  handleReset
} = useListQuery({
  apiFn: listLogicalModels,
  fields: { keyword: "", modelTypes: [] },
  pageSize: viewPageSize[viewMode.value]
});

const { options: modelTypeOptions, loadOptions: loadModelTypes } = useEnums("model_type");
const formVisible = ref(false);
const editingModel = ref(null);

function openCreate() {
  editingModel.value = null;
  formVisible.value = true;
}

function openEdit(row) {
  editingModel.value = row;
  formVisible.value = true;
}

function handleAction(action, row) {
  if (action === "edit") openEdit(row);
  if (action === "delete") removeModel(row);
}

async function removeModel(row) {
  await deleteLogicalModel(row.id);
  ElMessage.success("已删除统一模型");
  await resetPage();
}


// 分页条：按当前视图分别记住每页条数，卡片视图默认 12 条、列表视图默认 20 条
function onSizeChange(size) {
  viewPageSize[viewMode.value] = size;
  setStorageJson(VIEW_PAGE_SIZE_KEY, { ...viewPageSize });
  pageSize.value = size;
  pageNum.value = 1;
  return load();
}

function onViewModeChange(mode) {
  const next = mode === "card" ? "card" : "list";
  viewMode.value = next;
  setStorageItem(VIEW_MODE_KEY, next);
  pageSize.value = viewPageSize[next];
  return resetPage();
}

onMounted(() => {
  loadModelTypes();
  load();
});
</script>

<style scoped>
.market-toolbar__meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  min-width: 0;
}

@media (max-width: 768px) {
  .market-toolbar__meta {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
