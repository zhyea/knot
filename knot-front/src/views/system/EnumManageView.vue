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
      <el-table-column label="操作" width="220" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openItemsDrawer(row.category)">枚举</el-button>
          <el-button link type="primary" @click="openChangeLog(row.category)">日志</el-button>
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

    <el-drawer v-model="logDrawer" :title="`枚举变更日志 — ${logCategory || ''}`" size="640px" destroy-on-close>
      <el-table v-loading="logsLoading" :data="changeLogs" stripe border size="small" max-height="calc(100vh - 140px)">
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="operation" label="操作" width="90" />
        <el-table-column prop="entityName" label="对象" min-width="140" show-overflow-tooltip />
        <el-table-column prop="status" label="结果" width="88">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.status === "SUCCESS" ? "成功" : "失败" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="120" show-overflow-tooltip />
        <el-table-column prop="errorMsg" label="错误" min-width="120" show-overflow-tooltip />
      </el-table>
    </el-drawer>
  </PageSection>
</template>

<script setup>
import { ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import StatusTag from "../../components/common/StatusTag.vue";
import EnumCategoryCreateDialog from "../../components/system/EnumCategoryCreateDialog.vue";
import EnumItemListDrawer from "../../components/system/EnumItemListDrawer.vue";
import EnumItemFormDialog from "../../components/system/EnumItemFormDialog.vue";
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
const logsLoading = ref(false);
const changeLogs = ref([]);

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

async function openChangeLog(category) {
  logCategory.value = category;
  logDrawer.value = true;
  logsLoading.value = true;
  try {
    const data = await listEnumOperationLogs(category);
    changeLogs.value = Array.isArray(data) ? data : [];
  } finally {
    logsLoading.value = false;
  }
}

loadSummaries();
</script>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
</style>
