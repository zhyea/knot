<template>
  <div>
    <div class="toolbar">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="模块">
          <el-input v-model="query.module" placeholder="请输入模块" clearable />
        </el-form-item>
        <el-form-item label="操作">
          <el-input v-model="query.operation" placeholder="请输入操作" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <EnumSelect
            v-model="query.status"
            category="status"
            :include-codes="['SUCCESS', 'FAILURE']"
            placeholder="请选择状态"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="emit('query')">查询</el-button>
          <el-button @click="emit('reset')">重置</el-button>
        </el-form-item>
      </el-form>
      <el-button @click="emit('refresh')">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe border size="small" @row-click="(row) => emit('row-click', row)">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column prop="operation" label="操作" width="120" />
      <el-table-column prop="entityType" label="实体类型" width="120" />
      <el-table-column prop="entityName" label="实体名称" min-width="150" />
      <el-table-column prop="operatorName" label="操作人" width="100">
        <template #default="{ row }">{{ row.operatorName || "-" }}</template>
      </el-table-column>
      <el-table-column prop="ipAddress" label="IP地址" width="140" />
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

    <div class="pagination-wrap">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        :page-sizes="[10, 20, 50]"
        @current-change="(page) => emit('page-change', page)"
        @size-change="(size) => emit('size-change', size)"
      />
    </div>
    <p class="hint">点击行查看详细信息</p>
  </div>
</template>

<script setup>
import EnumSelect from "../common/EnumSelect.vue";

defineProps({
  query: { type: Object, required: true },
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  statusLabel: { type: Function, required: true }
});

const emit = defineEmits(["query", "reset", "refresh", "row-click", "page-change", "size-change"]);
</script>

<style scoped>
.query-form {
  flex: 1;
}

.hint {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
</style>
