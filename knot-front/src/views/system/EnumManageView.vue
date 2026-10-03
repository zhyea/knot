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

        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="code-enum-alert"
          title="代码枚举（只读）"
        >
          <template #default>
            <div class="code-enum-alert__head">
              <span>以下枚举由后端代码（EnumOptionRegistry）权威维护，不在 DB 字典中，禁止在上方重建。</span>
              <el-button link type="primary" size="small" @click="codeEnumCollapsed = !codeEnumCollapsed">
                {{ codeEnumCollapsed ? "展开" : "收起" }}
              </el-button>
            </div>
            <div v-show="!codeEnumCollapsed" class="code-enum-list">
              <div v-for="(options, key) in commonEnums" :key="key" class="code-enum-row">
                <span class="code-enum-key">{{ key }}</span>
                <div class="code-enum-tags">
                  <el-tag
                    v-for="(label, code) in options"
                    :key="code"
                    size="small"
                    type="info"
                    class="code-enum-tag"
                  >{{ code }} = {{ label }}</el-tag>
                </div>
              </div>
              <div v-if="Object.keys(commonEnums).length === 0" class="code-enum-empty">
                加载中或暂无可展示的代码枚举。
              </div>
            </div>
          </template>
        </el-alert>

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

<script setup lang="ts">
import type {Dict, Row} from "@/types";
import {ref, onMounted} from "vue";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import EnumCategoryCreateDialog from "../../components/system/EnumCategoryCreateDialog.vue";
import EnumCategoryListPanel from "../../components/system/EnumCategoryListPanel.vue";
import EnumItemListDrawer from "../../components/system/EnumItemListDrawer.vue";
import EnumItemFormDialog from "../../components/system/EnumItemFormDialog.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import {listEnumCategorySummaries, listEnumOperationLogs, listCommonEnums} from "@/api/enums";
import {useListQuery} from "@/composables/useListQuery";

const {
  query,
  rows: summaries,
  loading,
  load: loadSummaries,
  handleQuery,
  handleReset
} = useListQuery({ apiFn: listEnumCategorySummaries, fields: { keyword: "" } });

loadSummaries();

const commonEnums = ref<Dict>({});
const codeEnumCollapsed = ref(true);
onMounted(async () => {
  try {
    commonEnums.value = (await listCommonEnums()) as Dict;
  } catch {
    commonEnums.value = {};
  }
});

const itemsDrawerVisible = ref(false);
const currentCategory = ref("");
const itemListRef = ref<{ reload?: () => void } | null>(null);

const categoryCreateVisible = ref(false);

const itemFormVisible = ref(false);
const itemFormCategory = ref("");
const editingItem = ref<Dict | null>(null);

const logDrawer = ref(false);
const logCategory = ref("");

function openItemsDrawer(category: string) {
  currentCategory.value = category;
  itemsDrawerVisible.value = true;
}

function handleAction(action: string, row: Row) {
  if (action === "items") openItemsDrawer(row.category);
  if (action === "log") openChangeLog(row.category);
}

function openCreateItem() {
  editingItem.value = null;
  itemFormCategory.value = currentCategory.value;
  itemFormVisible.value = true;
}

function openEditItem(row: Row) {
  editingItem.value = row;
  itemFormCategory.value = row.category;
  itemFormVisible.value = true;
}

async function onItemFormSaved() {
  await loadSummaries();
  itemListRef.value?.reload?.();
}

function openChangeLog(category: string) {
  logCategory.value = category;
  logDrawer.value = true;
}

function loadEnumOperationLogs() {
  return listEnumOperationLogs(logCategory.value);
}
</script>

<style scoped>
.code-enum-alert {
  margin-bottom: 16px;
}
.code-enum-alert__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.code-enum-list {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.code-enum-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.code-enum-key {
  flex: 0 0 220px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  word-break: break-all;
}
.code-enum-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.code-enum-tag {
  font-family: monospace;
}
.code-enum-empty {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
