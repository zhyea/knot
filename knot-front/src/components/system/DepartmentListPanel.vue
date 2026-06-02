<template>
  <div>
    <div class="toolbar">
      <div class="toolbar__left">
        <el-button type="primary" @click="emit('create')">{{ createLabel }}</el-button>
        <el-button :disabled="!selectedNode" @click="emit('create-child')">新增下级</el-button>
      </div>
      <el-button @click="emit('refresh')">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="deptCode" label="部门编码" min-width="140" show-overflow-tooltip/>
      <el-table-column prop="deptName" label="部门名称" min-width="140" show-overflow-tooltip/>
      <el-table-column label="层级" width="90" align="center" header-align="center">
        <template #default="{ row }">第 {{ row.level || 1 }} 级</template>
      </el-table-column>
      <el-table-column label="状态" width="120" align="center" header-align="center">
        <template #default="{ row }">
          <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              active-text="启用"
              inactive-text="禁用"
              inline-prompt
              @change="(value) => emit('status-change', row, value)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
              :actions="[
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'log', label: '日志', icon: Document },
              { key: 'delete', label: '删除', icon: Delete, type: 'danger' }
            ]"
              @action="(action) => emit('action', action, row)"
          />
        </template>
      </el-table-column>
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
  </div>
</template>

<script setup>
import {Delete, Document, Edit} from "@element-plus/icons-vue";
import RowActions from "../common/RowActions.vue";

defineProps({
  rows: {type: Array, default: () => []},
  loading: {type: Boolean, default: false},
  total: {type: Number, default: 0},
  pageNum: {type: Number, default: 1},
  pageSize: {type: Number, default: 20},
  selectedNode: {type: Object, default: null},
  createLabel: {type: String, default: "新建部门"}
});

const emit = defineEmits(["create", "create-child", "refresh", "status-change", "action", "page-change", "size-change"]);

function formatDateTime(dateTime) {
  if (!dateTime) return "-";
  return new Date(dateTime).toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  });
}
</script>

<style scoped>
.toolbar__left {
  display: flex;
  gap: 8px;
}
</style>
