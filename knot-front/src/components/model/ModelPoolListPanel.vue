<template>
  <div>
    <el-table
      v-loading="loading"
      :data="rows"
      :row-class-name="rowClassName"
      stripe
      border
      style="width: 100%"
    >
      <el-table-column prop="id" label="ID" width="60" align="center" header-align="center" fixed="left"/>
      <el-table-column prop="poolCode" label="模型池编码" min-width="200" show-overflow-tooltip />
      <el-table-column label="统一模型" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="bind-list__text">{{ row.logicalModelCode || row.logicalModelName || "—" }}</span>
        </template>
      </el-table-column>
      <el-table-column label="选择策略" min-width="70">
        <template #default="{ row }">{{ strategyLabel(row.selectionStrategy) }}</template>
      </el-table-column>
      <el-table-column label="模型数量" width="60" align="center">
        <template #default="{ row }">{{ row.items?.length || 0 }}</template>
      </el-table-column>
      <el-table-column label="启用" width="88" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled !== false"
            :loading="togglingId === row.id"
            :disabled="row.deleted === true"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
            @change="(value) => handleEnabledChange(row, value)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="rowActions(row)"
            @action="(action) => emit(action, row)"
          />
        </template>
      </el-table-column>
    </el-table>

    <ListPagination
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      :show-refresh="showRefresh"
      @refresh="emit('refresh')"
      @page-change="(page) => emit('page-change', page)"
      @size-change="(size) => emit('size-change', size)"
    />
  </div>
</template>

<script setup lang="ts">
import type {Row, RowAction} from "@/types";
import {Delete, Document, Edit, RefreshLeft} from "@element-plus/icons-vue";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";
import {updateModelPoolStatus} from "@/api/modelPools";
import {useEnabledToggle} from "@/composables/useEnabledToggle";
import {useEnumOptions} from "@/composables/useEnumOptions";

defineProps({
  rows: { type: Array, default: (): Row[] => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  showRefresh: { type: Boolean, default: true }
});

const emit = defineEmits([
  "create",
  "refresh",
  "edit",
  "log",
  "delete",
  "page-change",
  "size-change",
  "changed"
]);

const { labelOf } = useEnumOptions();
const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateModelPoolStatus
});

function modelTypeLabel(code: string) {
  return labelOf("ModelTypeEnum", code, code || "-");
}

function strategyLabel(code: string) {
  return labelOf("ModelPoolSelectionStrategyEnum", code, code || "-");
}

/** 已删除行：浅红底标识（排序已由后端 is_deleted asc 放到末尾） */
function rowClassName({ row }: { row: Row }) {
  return row.deleted === true ? "row-deleted" : "";
}

/** 已删除行只给「日志 / 恢复」，编辑与删除都无意义；恢复后按常规操作列展示 */
function rowActions(row: Row): RowAction[] {
  if (row.deleted === true) {
    return [
      { key: "log", label: "日志", icon: Document },
      { key: "restore", label: "恢复", icon: RefreshLeft, type: "success", confirm: "确认恢复该模型池？" }
    ];
  }
  return [
    { key: "edit", label: "编辑", icon: Edit },
    { key: "log", label: "日志", icon: Document },
    { key: "delete", label: "删除", icon: Delete, type: "danger" }
  ];
}

async function handleEnabledChange(row: Row, enabled: string | number | boolean) {
  await onEnabledChange(row, enabled);
  emit("changed");
}
</script>

<style scoped>
.bind-list__text {
  overflow: hidden;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 已逻辑删除的行：浅红底 + 文字降透明度，与正常行一眼可分 */
:deep(.el-table__row.row-deleted > td) {
  background: #fef0f0 !important;
  color: #c45656;
}

:deep(.el-table__row.row-deleted > td .bind-list__text) {
  color: #c45656;
  text-decoration: line-through;
}
</style>
