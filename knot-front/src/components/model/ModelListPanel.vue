<template>
  <div>
    <el-table v-loading="loading" :data="rows" :row-class-name="rowClassName" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center" header-align="center" />
      <el-table-column prop="modelCode" label="模型编码" width="220" show-overflow-tooltip />
      <el-table-column prop="logicalModelCode" label="统一模型" width="240" show-overflow-tooltip />
      <el-table-column label="供应商" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.providerName || (row.providerAccountCode ? `#${row.providerAccountCode}` : "-") }}
        </template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          {{ modelTypeLabel(row.modelType) }}
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="136" align="center" show-overflow-tooltip />
      <el-table-column label="启用" width="88" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="isRowEnabled(row)"
            :loading="togglingId === row.id"
            :disabled="isDeletedRow(row)"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
            @change="(val) => handleEnabledChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="180" align="center" show-overflow-tooltip>
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="180" align="center" show-overflow-tooltip>
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" align="center" header-align="center" fixed="right">
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
import {isRowEnabled} from "@/utils/enabledFlag";
import type {PropType} from "vue";
import type {Row, RowAction} from "@/types";
import {CopyDocument, Delete, Discount, Document, Edit, RefreshLeft} from "@element-plus/icons-vue";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";
import {updateModelStatus} from "@/api/models";
import {useEnabledToggle} from "@/composables/useEnabledToggle";

import {formatDateTime} from "@/utils/format";
import {useModelTypeText} from "@/composables/useEnumText";
import {deletedRowClassName, isDeletedRow} from "@/utils/rowState";

defineProps({
  rows: { type: Array as PropType<Row[]>, default: (): Row[] => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  showRefresh: { type: Boolean, default: true }
});

const emit = defineEmits(["create", "refresh", "edit", "copy", "discount", "log", "delete", "restore", "page-change", "size-change", "changed"]);


const {togglingId, handleEnabledChange} = useEnabledToggle({
  updateApi: updateModelStatus,
  onChanged: () => emit("changed")
});

const modelTypeLabel = useModelTypeText();
/** 已删除行：浅红底标识（排序已由后端 is_deleted asc 放到末尾） */
const rowClassName = deletedRowClassName;

/** 已删除行只给「日志 / 恢复」，编辑 / 复制 / 删除都无意义；恢复后按常规操作列展示 */
function rowActions(row: Row): RowAction[] {
  if (isDeletedRow(row)) {
    return [
      { key: "log", label: "日志", icon: Document },
      { key: "restore", label: "恢复", icon: RefreshLeft, type: "success", confirm: "确认恢复该供应商模型？" }
    ];
  }
  return [
    { key: "edit", label: "编辑", icon: Edit },
    { key: "copy", label: "复制", icon: CopyDocument },
    { key: "discount", label: "折扣策略", icon: Discount },
    { key: "log", label: "日志", icon: Document },
    { key: "delete", label: "删除", icon: Delete, type: "danger" }
  ];
}

</script>
