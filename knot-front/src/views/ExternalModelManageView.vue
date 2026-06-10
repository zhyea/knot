<template>
  <PageSection>
    <div class="list-page-shell">
      <section class="list-page-block">
        <div class="list-page-filters">
          <div class="list-filter-item external-model-filter-item">
            <span class="list-filter-label">来源</span>
            <el-select
              v-model="query.sourceCode"
              class="list-filter-control external-model-filter-control"
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
          </div>
          <div class="list-filter-item external-model-filter-item external-model-filter-item--type">
            <span class="list-filter-label">模型类型</span>
            <EnumSelect
              v-model="query.modelType"
              class="list-filter-control external-model-filter-control external-model-filter-control--type"
              category="model_type"
              clearable
            />
          </div>
          <div class="list-filter-item list-filter-item--grow">
            <span class="list-filter-label">关键词</span>
            <el-input
              v-model="query.keyword"
              class="list-filter-control--wide"
              placeholder="按模型名、模型 ID、供应商筛选"
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
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import EnumSelect from "../components/common/EnumSelect.vue";
import ExternalModelDetailDrawer from "../components/model/ExternalModelDetailDrawer.vue";
import ExternalModelListPanel from "../components/model/ExternalModelListPanel.vue";
import { useAutoQuery } from "../composables/useAutoQuery";
import { usePageList } from "../composables/usePageList";
import {
  createLogicalModelFromExternalItem,
  createLogicalModelsFromExternalItems,
  deleteExternalModelItem,
  deleteExternalModelItems,
  getExternalModelItem,
  listExternalModelItems,
  listExternalModelSources,
  syncExternalModelSource
} from "../api/externalModels";

const query = reactive({
  sourceCode: "OPENROUTER",
  keyword: "",
  modelType: ""
});

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listExternalModelItems, { extra: query });
const { pauseAutoQuery } = useAutoQuery(query, handleQuery);

const sources = ref([]);
const syncing = ref(false);
const creatingAll = ref(false);
const detailVisible = ref(false);
const detail = ref(null);
const selectedRows = ref([]);

const createAllDisabled = computed(() => creatingAll.value || total.value <= 0);

async function loadSources() {
  sources.value = await listExternalModelSources();
}

async function syncOpenRouter() {
  syncing.value = true;
  try {
    const result = await syncExternalModelSource("OPENROUTER");
    ElMessage.success(result?.message || "同步完成");
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
  await resetPage();
}

async function createAllVisible() {
  creatingAll.value = true;
  try {
    const result = await createLogicalModelsFromExternalItems({
      sourceCode: query.sourceCode || "",
      keyword: query.keyword || "",
      modelType: query.modelType || ""
    });
    ElMessage.success(result?.message || `已创建 ${result?.inserted || 0} 个统一模型`);
    await resetPage();
  } finally {
    creatingAll.value = false;
  }
}

async function deleteOne(row) {
  await deleteExternalModelItem(row.id);
  ElMessage.success("已删除外部模型");
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

function handleQuery() {
  return pauseAutoQuery(() => resetPage());
}

function handleReset() {
  return pauseAutoQuery(() => {
    query.sourceCode = "OPENROUTER";
    query.keyword = "";
    query.modelType = "";
    return resetPage();
  });
}

onMounted(() => {
  loadSources();
  load();
});
</script>

<style scoped>
.external-model-filter-item {
  min-width: 220px;
}

.external-model-filter-item--type {
  min-width: 252px;
}

.external-model-filter-control {
  width: 190px;
}

.external-model-filter-control--type {
  width: 220px;
}

@media (max-width: 768px) {
  .external-model-filter-item,
  .external-model-filter-item--type {
    min-width: 0;
  }

  .external-model-filter-control,
  .external-model-filter-control--type {
    width: 100%;
  }
}
</style>
