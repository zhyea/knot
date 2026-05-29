<template>
  <PageSection title="外部模型">
    <div class="toolbar">
      <el-select v-model="extra.sourceCode" placeholder="来源" class="source-select" @change="resetPage">
        <el-option v-for="s in sources" :key="s.sourceCode" :label="s.sourceName" :value="s.sourceCode" />
      </el-select>
      <el-button @click="load">刷新</el-button>
      <el-button type="primary" :loading="syncing" @click="syncAiBase">同步 AIBase 商用模型</el-button>
      <el-button type="success" :loading="creatingAll" :disabled="createAllDisabled" @click="createAllVisible">
        一键创建统一模型
      </el-button>
      <el-popconfirm title="确认物理删除选中的外部模型？" @confirm="deleteSelected">
        <template #reference>
          <el-button type="danger" :disabled="!selectedRows.length">批量删除</el-button>
        </template>
      </el-popconfirm>
    </div>

    <ExternalModelListPanel
      :rows="rows"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      @selection-change="handleSelectionChange"
      @action="handleAction"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    />

    <ExternalModelDetailDrawer v-model="detailVisible" :detail="detail" />
  </PageSection>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import ExternalModelDetailDrawer from "../components/model/ExternalModelDetailDrawer.vue";
import ExternalModelListPanel from "../components/model/ExternalModelListPanel.vue";
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

const { rows, loading, total, pageNum, pageSize, extra, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listExternalModelItems, {
    extra: { sourceCode: "AIBASE", syncStatus: "" }
  });

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

async function syncAiBase() {
  syncing.value = true;
  try {
    const result = await syncExternalModelSource("AIBASE");
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
      sourceCode: extra.sourceCode || "",
      syncStatus: extra.syncStatus || ""
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

onMounted(() => {
  loadSources();
  load();
});
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.source-select {
  width: 180px;
}
</style>
