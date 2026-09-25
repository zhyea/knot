<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按模型名、模型 ID、供应商筛选"
          @query="handleQuery"
        />
        <FilterField label="来源" :width="180">
          <el-select
            v-model="query.sourceCode"
            placeholder="来源"
            clearable
          >
            <el-option
              v-for="source in sources"
              :key="source.sourceCode"
              :label="source.sourceName"
              :value="source.sourceCode"
            />
          </el-select>
        </FilterField>
        <FilterField label="模型类型" :width="260">
          <EnumControl
            v-model="query.modelType"
            enum-name="ModelTypeEnum"
            clearable
          />
        </FilterField>
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" :loading="syncing" @click="syncOpenRouter">
              同步 OpenRouter 模型
            </el-button>
            <el-button
              type="success"
              :loading="creatingAll"
              :disabled="createAllDisabled"
              @click="createAllVisible"
            >
              一键创建统一模型
            </el-button>
            <el-popconfirm title="确认物理删除选中的外部模型？" @confirm="deleteSelected">
              <template #reference>
                <el-button type="danger" :disabled="!selectedRows.length">批量删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>

        <ExternalModelListPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :show-refresh="false"
          @selection-change="handleSelectionChange"
          @action="handleAction"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <ExternalModelDetailDrawer v-model="detailVisible" :detail="detail" />
  </PageSection>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import { useListQuery } from "../../composables/useListQuery";
import EnumControl from "../../components/common/EnumControl.vue";
import ExternalModelDetailDrawer from "../../components/model/ExternalModelDetailDrawer.vue";
import ExternalModelListPanel from "../../components/model/ExternalModelListPanel.vue";
import {
  createLogicalModelFromExternalItem,
  createLogicalModelsFromExternalItems,
  deleteExternalModelItem,
  deleteExternalModelItems,
  getExternalModelItem,
  listExternalModelItems,
  listExternalModelSources,
  syncExternalModelSource
} from "../../api/externalModels";

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
} = useListQuery({ apiFn: listExternalModelItems, fields: { sourceCode: "OPENROUTER", keyword: "", modelType: "" } });

const sources = ref([]);
const syncing = ref(false);
const creatingAll = ref(false);
const detailVisible = ref(false);
const detail = ref(null);
const selectedRows = ref([]);

const createAllDisabled = computed(() => creatingAll.value || selectedRows.value.length === 0);

async function loadSources() {
  sources.value = await listExternalModelSources();
}

async function syncOpenRouter() {
  syncing.value = true;
  try {
    const result = await syncExternalModelSource("OPENROUTER");
    ElMessage.success(result?.message || "同步完成");
    selectedRows.value = [];
    await resetPage();
  } finally {
    syncing.value = false;
  }
}

async function handleAction(action, row) {
  if (action === "view") await openDetail(row);
  if (action === "create") await createOne(row);
  if (action === "delete") await deleteOne(row);
}

function handleSelectionChange(selection) {
  selectedRows.value = selection;
}

async function openDetail(row) {
  detail.value = await getExternalModelItem(row.id);
  detailVisible.value = true;
}

async function createOne(row) {
  await createLogicalModelFromExternalItem(row.id);
  ElMessage.success("已创建统一模型");
  selectedRows.value = [];
  await resetPage();
}

async function createAllVisible() {
  const ids = selectedRows.value.map((row) => row.id).filter((id) => id != null);
  if (!ids.length) {
    ElMessage.warning("请选择要创建统一模型的外部模型");
    return;
  }
  creatingAll.value = true;
  try {
    const result = await createLogicalModelsFromExternalItems({
      ids
    });
    ElMessage.success(result?.message || `已创建 ${result?.inserted || 0} 个统一模型`);
    selectedRows.value = [];
    await resetPage();
  } finally {
    creatingAll.value = false;
  }
}

async function deleteOne(row) {
  await deleteExternalModelItem(row.id);
  ElMessage.success("已删除外部模型");
  selectedRows.value = [];
  await resetPage();
}

async function deleteSelected() {
  const ids = selectedRows.value.map((row) => row.id);
  if (!ids.length) {
    ElMessage.warning("请选择要删除的外部模型");
    return;
  }
  const affected = await deleteExternalModelItems(ids);
  ElMessage.success(`已删除 ${affected || ids.length} 个外部模型`);
  selectedRows.value = [];
  await resetPage();
}


onMounted(() => {
  loadSources();
  load();
});
</script>
