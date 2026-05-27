<template>
  <PageSection title="枚举管理">
    <div class="toolbar">
      <el-button type="primary" @click="categoryCreateVisible = true">新增分类首项</el-button>
      <el-button @click="loadSummaries">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="summaries" stripe border size="small">
      <el-table-column prop="category" label="分类编码" min-width="140" show-overflow-tooltip />
      <el-table-column prop="categoryName" label="分类名称" min-width="120" show-overflow-tooltip />
      <el-table-column label="系统" width="72" align="center">
        <template #default="{ row }">
          <StatusTag :active="row.isSystem" />
        </template>
      </el-table-column>
      <el-table-column prop="itemCount" label="枚举项数" width="110" align="center" />
      <el-table-column prop="enabledCount" label="已启用" width="100" align="center" />
      <el-table-column label="操作" width="110" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'items', label: '枚举项', icon: Tickets },
              { key: 'log', label: '日志', icon: Document }
            ]"
            @action="(action) => handleAction(action, row)"
          />
        </template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!loading && summaries.length === 0"
      description="暂无枚举分类。请执行库表初始化（enum_categories / enum_configs）或点击「新增分类首项」创建。"
    />

    <EnumCategoryCreateDialog v-model="categoryCreateVisible" @saved="loadSummaries" />

    <EnumItemListDrawer
      ref="itemListRef"
      v-model="itemsDrawerVisible"
      :category="currentCategory"
      @create="openCreateItem"
      @edit="openEditItem"
      @changed="loadSummaries"
    />

    <EnumItemFormDialog
      v-model="itemFormVisible"
      :category="itemFormCategory"
      :item="editingItem"
      @saved="onItemFormSaved"
    />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`枚举变更日志 — ${logCategory || ''}`"
      :load-logs="loadEnumOperationLogs"
    />
  </PageSection>
</template>

<script setup>
import { ref } from "vue";
import { Document, Tickets } from "@element-plus/icons-vue";
import PageSection from "../../components/common/PageSection.vue";
import RowActions from "../../components/common/RowActions.vue";
import StatusTag from "../../components/common/StatusTag.vue";
import EnumCategoryCreateDialog from "../../components/system/EnumCategoryCreateDialog.vue";
import EnumItemListDrawer from "../../components/system/EnumItemListDrawer.vue";
import EnumItemFormDialog from "../../components/system/EnumItemFormDialog.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import { listEnumCategorySummaries, listEnumOperationLogs } from "../../api/enums";

const loading = ref(false);
const summaries = ref([]);

const categoryCreateVisible = ref(false);

const itemsDrawerVisible = ref(false);
const currentCategory = ref("");
const itemListRef = ref(null);

const itemFormVisible = ref(false);
const itemFormCategory = ref("");
const editingItem = ref(null);

const logDrawer = ref(false);
const logCategory = ref("");

async function loadSummaries() {
  loading.value = true;
  try {
    const data = await listEnumCategorySummaries();
    summaries.value = Array.isArray(data) ? data : [];
  } catch {
    summaries.value = [];
  } finally {
    loading.value = false;
  }
}

function openItemsDrawer(category) {
  currentCategory.value = category;
  itemsDrawerVisible.value = true;
}

function handleAction(action, row) {
  if (action === "items") openItemsDrawer(row.category);
  if (action === "log") openChangeLog(row.category);
}

function openCreateItem() {
  editingItem.value = null;
  itemFormCategory.value = currentCategory.value;
  itemFormVisible.value = true;
}

function openEditItem(row) {
  editingItem.value = row;
  itemFormCategory.value = row.category;
  itemFormVisible.value = true;
}

async function onItemFormSaved() {
  await loadSummaries();
  itemListRef.value?.reload?.();
}

function openChangeLog(category) {
  logCategory.value = category;
  logDrawer.value = true;
}

function loadEnumOperationLogs() {
  return listEnumOperationLogs(logCategory.value);
}

loadSummaries();
</script>
