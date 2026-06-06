<template>
  <div>
    <el-table
        v-loading="loading"
        :data="rows"
        stripe
        border
        style="width: 100%"
        @selection-change="(selection) => emit('selection-change', selection)"
    >
      <el-table-column type="selection" width="48" fixed="left"/>
      <el-table-column prop="modelName" label="模型名称" min-width="220" show-overflow-tooltip/>
      <el-table-column prop="modelId" label="模型 ID" min-width="220" show-overflow-tooltip/>
      <el-table-column prop="providerName" label="供应商" min-width="130" show-overflow-tooltip/>
      <el-table-column prop="modelType" label="类型" min-width="110" show-overflow-tooltip/>
      <el-table-column prop="contextLength" label="上下文" min-width="100" show-overflow-tooltip/>
      <el-table-column label="创建时间" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ formatDateTime(row.modelCreatedAt || row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
              :actions="[
              { key: 'view', label: '查看', icon: View },
              { key: 'create', label: '一键创建统一模型', icon: Plus, hidden: row.logicalModelId },
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
      @refresh="emit('refresh')"
      @page-change="(page) => emit('page-change', page)"
      @size-change="(size) => emit('size-change', size)"
    />
  </div>
</template>

<script setup>
import {Delete, Plus, View} from "@element-plus/icons-vue";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";

defineProps({
  rows: {type: Array, default: () => []},
  loading: {type: Boolean, default: false},
  total: {type: Number, default: 0},
  pageNum: {type: Number, default: 1},
  pageSize: {type: Number, default: 20}
});

const emit = defineEmits(["selection-change", "action", "refresh", "page-change", "size-change"]);

function formatDateTime(value) {
  if (!value) return "—";
  return String(value).replace("T", " ").slice(0, 19);
}
</script>
