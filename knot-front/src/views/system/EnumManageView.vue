<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按分类编码、名称筛选"
          @query="handleQuery"
        />
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="categoryCreateVisible = true">新增分类</el-button>
          </div>
        </div>

        <EnumCategoryListPanel
          :summaries="summaries"
          :loading="loading"
          @action="handleAction"
        />
      </section>
    </div>

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
      :title="`枚举变更日志 - ${logCategory || ''}`"
      :load-logs="loadEnumOperationLogs"
    />
  </PageSection>
</template>

<script setup>
import { ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import EnumCategoryCreateDialog from "../../components/system/EnumCategoryCreateDialog.vue";
import EnumCategoryListPanel from "../../components/system/EnumCategoryListPanel.vue";
import EnumItemListDrawer from "../../components/system/EnumItemListDrawer.vue";
import EnumItemFormDialog from "../../components/system/EnumItemFormDialog.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import { listEnumCategorySummaries, listEnumOperationLogs } from "../../api/enums";
import { useListQuery } from "../../composables/useListQuery";

const {
  query,
  rows: summaries,
  loading,
  load: loadSummaries,
  handleQuery,
  handleReset
} = useListQuery({ apiFn: listEnumCategorySummaries, fields: { keyword: "" } });

loadSummaries();

const itemsDrawerVisible = ref(false);
const currentCategory = ref("");
const itemListRef = ref(null);

const itemFormVisible = ref(false);
const itemFormCategory = ref("");
const editingItem = ref(null);

const logDrawer = ref(false);
const logCategory = ref("");

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
</script>
