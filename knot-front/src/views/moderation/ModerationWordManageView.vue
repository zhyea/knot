<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按敏感词筛选"
          @query="handleQuery"
        />
        <FilterField label="分类" :width="200">
          <EnumSelect v-model="query.categoryCode" category="moderation_category" clearable placeholder="全部分类" />
        </FilterField>
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建敏感词</el-button>
            <el-button @click="importVisible = true">批量导入</el-button>
            <el-button :loading="exporting" @click="exportCsv">导出 CSV</el-button>
          </div>
          <div class="list-page-toolbar__actions list-page-toolbar__actions--end">
            <el-tooltip content="展示已删除词条（可恢复）" placement="top">
              <el-checkbox v-model="includeDeleted" @change="handleQuery">含已删除</el-checkbox>
            </el-tooltip>
          </div>
        </div>

        <el-table v-loading="loading" :data="rows" stripe border style="width: 100%" row-key="id">
          <el-table-column prop="id" label="ID" width="70" align="center" header-align="center" fixed="left"/>
          <el-table-column prop="word" label="敏感词" min-width="200" show-overflow-tooltip fixed="left">
            <template #default="{ row }">
              <span :class="{ 'word-deleted': row.deleted }">{{ row.word }}</span>
            </template>
          </el-table-column>
          <el-table-column label="分类" width="120" align="center">
            <template #default="{ row }">
              <EnumSelect
                :model-value="row.categoryCode"
                category="moderation_category"
                :disabled="true"
                code-only
              />
            </template>
          </el-table-column>
          <el-table-column label="严重度" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="severityTagType(row.severity)" size="small">
                {{ severityLabel(row.severity) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lang" label="语言" width="80" align="center" />
          <el-table-column label="启用" width="88" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="row.enabled !== false"
                :loading="togglingId === row.id"
                :disabled="row.deleted"
                inline-prompt
                active-text="启用"
                inactive-text="禁用"
                @change="(value) => onEnabledChange(row, value)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          <el-table-column prop="updatedAt" label="更新时间" width="170" align="center" />
          <el-table-column label="操作" width="150" align="center" header-align="center" fixed="right">
            <template #default="{ row }">
              <RowActions
                :actions="rowActions(row)"
                @action="(action) => handleAction(action, row)"
              />
            </template>
          </el-table-column>
        </el-table>

        <ListPagination
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :show-refresh="false"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <ModerationWordFormDialog v-model="formVisible" :word="editingWord" @saved="resetPage" />
    <ModerationWordImportDialog v-model="importVisible" @imported="resetPage" />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`敏感词操作日志 - ${logName || ''}`"
      :load-logs="loadWordLogs"
    />
  </PageSection>
</template>

<script setup lang="ts">
import type {Dict, Row, RowAction} from "@/types";
import type {ButtonType} from "element-plus";
import {onMounted, ref} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import {Delete, Document, Edit, RefreshLeft} from "@element-plus/icons-vue";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import EnumSelect from "../../components/common/EnumSelect.vue";
import ListPagination from "../../components/common/ListPagination.vue";
import RowActions from "../../components/common/RowActions.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import ModerationWordFormDialog from "../../components/moderation/ModerationWordFormDialog.vue";
import ModerationWordImportDialog from "../../components/moderation/ModerationWordImportDialog.vue";
import {useListQuery} from "@/composables/useListQuery";
import {useEnabledToggle} from "@/composables/useEnabledToggle";
import {
  deleteModerationWord,
  exportModerationWords,
  listModerationWords,
  restoreModerationWord,
  updateModerationWordStatus
} from "@/api/moderationWords";
import {listOperationLogsByEntity} from "@/api/operationLogs";

/**
 * 敏感词库管理页（内容安全模块，权限 moderation:word:*）。
 *
 * 词条是网关同步插件的自动机数据源：本页写入后本节点缓存立即失效，
 * 其余网关节点最迟 5s 内经版本轮询生效（见 ModerationSnapshotCache）。
 *
 * 「含已删除」是独立开关而非 query 字段：useListQuery 的 normalize 会把布尔值
 * 转成 "true"/"false" 字符串下发，依赖 Jackson 强转；这里显式传布尔更稳。
 */
const includeDeleted = ref(false);

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
  apiFn: (params: Dict) => listModerationWords({...params, includeDeleted: includeDeleted.value}),
  fields: { keyword: "", categoryCode: "" }
});

const formVisible = ref(false);
const importVisible = ref(false);
const exporting = ref(false);
const editingWord = ref<Row | null>(null);

const logDrawer = ref(false);
const logId = ref<number | string | null>(null);
const logName = ref("");

const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateModerationWordStatus
});

const severityLabels: Record<number, string> = {1: "低", 2: "中", 3: "高"};

function severityLabel(severity: number): string {
  return severityLabels[severity] || "中";
}

function severityTagType(severity: number): "info" | "warning" | "danger" {
  if (severity === 3) return "danger";
  if (severity === 1) return "info";
  return "warning";
}

function openCreate() {
  editingWord.value = null;
  formVisible.value = true;
}

function openEdit(row: Row) {
  editingWord.value = row;
  formVisible.value = true;
}

async function remove(row: Row) {
  await ElMessageBox.confirm(
      `确认删除敏感词“${row.word}”？删除后同分类下无法新建同名词，只能恢复。`,
      "删除确认",
      {type: "warning"}
  );
  await deleteModerationWord(row.id);
  ElMessage.success("已删除");
  resetPage();
}

async function restore(row: Row) {
  await restoreModerationWord(row.id);
  ElMessage.success("已恢复");
  resetPage();
}

function openLog(row: Row) {
  logId.value = row.id;
  logName.value = row.word || `#${row.id}`;
  logDrawer.value = true;
}

function loadWordLogs() {
  return listOperationLogsByEntity({
    module: "moderation",
    entityType: "ModerationWord",
    entityId: logId.value!
  });
}

function rowActions(row: Row): RowAction[] {
  if (row.deleted) {
    return [
      {key: "restore", label: "恢复", icon: RefreshLeft},
      {key: "log", label: "操作日志", icon: Document}
    ];
  }
  return [
    {key: "edit", label: "编辑", icon: Edit},
    {key: "log", label: "操作日志", icon: Document},
    {key: "delete", label: "删除", icon: Delete, type: "danger" as ButtonType}
  ];
}

function handleAction(action: string, row: Row) {
  if (action === "edit") openEdit(row);
  if (action === "delete") remove(row);
  if (action === "restore") restore(row);
  if (action === "log") openLog(row);
}

/** 导出全部未删除词条（含停用）为 CSV：含 BOM，Excel 直接打开不乱码。 */
async function exportCsv() {
  exporting.value = true;
  try {
    const list = (await exportModerationWords()) as Row[];
    if (!Array.isArray(list) || list.length === 0) {
      ElMessage.warning("没有可导出的词条");
      return;
    }
    const header = ["敏感词", "分类编码", "严重度", "语言", "启用", "备注"];
    const body = list.map((row) => [
      row.word,
      row.categoryCode,
      row.severity,
      row.lang || "zh",
      row.enabled === false ? "否" : "是",
      row.remark || ""
    ]);
    const csv = "\uFEFF" + [header, ...body]
        .map((cells) => cells.map((cell) => `"${String(cell ?? "").replace(/"/g, '""')}"`).join(","))
        .join("\r\n");
    const blob = new Blob([csv], {type: "text/csv;charset=utf-8"});
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `sensitive-words-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
    ElMessage.success(`已导出 ${list.length} 条`);
  } finally {
    exporting.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.word-deleted {
  color: var(--el-text-color-disabled);
  text-decoration: line-through;
}
</style>
