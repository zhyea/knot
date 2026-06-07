<template>
  <div>
    <el-table
      v-loading="loading"
      :data="rows"
      stripe
      border
      size="small"
      @row-click="(row) => emit('row-click', row)"
    >
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column prop="operation" label="操作" width="120" />
      <el-table-column prop="entityType" label="实体类型" width="120" />
      <el-table-column prop="entityName" label="实体名称" min-width="150" />
      <el-table-column prop="operatorName" label="操作人" width="100">
        <template #default="{ row }">{{ row.operatorName || "-" }}</template>
      </el-table-column>
      <el-table-column prop="ipAddress" label="IP 地址" width="140" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="executionTime" label="执行时间(ms)" width="120" />
      <el-table-column prop="createdAt" label="操作时间" width="180" />
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

    <p class="hint">点击行查看详细信息</p>
  </div>
</template>

<script setup>
import ListPagination from "../common/ListPagination.vue";

defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  statusLabel: { type: Function, required: true },
  showRefresh: { type: Boolean, default: true }
});

const emit = defineEmits(["refresh", "row-click", "page-change", "size-change"]);
</script>

<style scoped>
.hint {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
</style>
