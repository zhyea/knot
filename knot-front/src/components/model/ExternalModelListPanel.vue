<template>
  <div>
    <el-table
      v-loading="loading"
      :data="rows"
      stripe
      border
      style="width: 100%"
      class="external-model-table"
      :row-class-name="rowClassName"
      scrollbar-always-on
      @selection-change="(selection) => emit('selection-change', selection)"
    >
      <el-table-column type="selection" width="48" fixed="left" />
      <el-table-column prop="modelId" label="模型 ID" width="280">
        <template #default="{ row }">
          <span class="cell-wrap">{{ row.modelId || "-" }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="modelName" label="模型名称" width="240">
        <template #default="{ row }">
          <span class="cell-wrap">{{ row.modelName || "-" }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="providerName" label="供应商" width="150">
        <template #default="{ row }">
          <span class="cell-wrap">{{ row.providerName || "-" }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="120">
        <template #default="{ row }">{{ modelTypeLabel(row.modelType) }}</template>
      </el-table-column>
      <el-table-column
        prop="contextLength"
        label="上下文"
        width="140"
        align="left"
        header-align="left"
      >
        <template #default="{ row }">
          <span class="cell-wrap cell-num">{{ formatThousands(row.contextLength, "-") }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.modelCreatedAt || row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'view', label: '查看', icon: Search },
              { key: 'create', label: '一键创建统一模型', icon: Plus, hidden: row.logicalModelId },
              { key: row.ignored ? 'unignore' : 'ignore', label: row.ignored ? '解除忽略' : '忽略', icon: row.ignored ? View : Hide },
              { key: 'delete', label: '删除', icon: Delete, type: 'danger', confirm: '确认物理删除该外部模型？' }
            ]"
            @action="(action) => emit('action', action, row)"
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
import {Delete, Hide, Plus, Search, View} from "@element-plus/icons-vue";
import type {Row} from "@/types";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {formatThousands} from "@/utils/format";

const { labelOf } = useEnumOptions();

defineProps({
  rows: { type: Array, default: (): Row[] => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  showRefresh: { type: Boolean, default: true }
});

const emit = defineEmits(["selection-change", "action", "refresh", "page-change", "size-change"]);

function modelTypeLabel(code: string): string {
  if (!code) return "-";
  return labelOf("ModelTypeEnum", code, code);
}

function formatDateTime(value: unknown): string {
  if (!value) return "-";
  return String(value).replace("T", " ").slice(0, 19);
}

function rowClassName({ row }: { row: Row }): string {
  return row.logicalModelId == null ? "external-model-uncreated-row" : "";
}
</script>

<style scoped>
:deep(.external-model-table .el-table__cell) {
  word-break: break-word;
}

.cell-wrap {
  display: inline-block;
  max-width: 100%;
  white-space: normal;
  word-break: break-word;
  line-height: 1.5;
}

/* 数值列：左对齐 + 等宽数字，千分位分隔后位数对齐更好读 */
.cell-num {
  text-align: left;
  font-variant-numeric: tabular-nums;
}

:deep(.external-model-table .el-table__body tr.external-model-uncreated-row > td.el-table__cell) {
  background-color: #f0f9eb;
}

:deep(.el-table__body tr.external-model-uncreated-row:hover > td.el-table__cell) {
  background-color: #e1f3d8;
}
</style>
