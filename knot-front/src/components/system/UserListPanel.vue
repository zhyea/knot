<template>
  <div>
    <el-table v-loading="loading" :data="users" stripe border>
      <el-table-column prop="id" label="ID" min-width="5%" align="center" header-align="center" />
      <el-table-column prop="username" label="用户名" min-width="12%" />
      <el-table-column prop="realName" label="姓名" min-width="10%" />
      <el-table-column prop="deptName" label="所属部门" min-width="12%">
        <template #default="{ row }">{{ row.deptName || "-" }}</template>
      </el-table-column>
      <el-table-column label="状态" min-width="12%">
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
      <el-table-column prop="lastLoginTime" label="上次登录时间" min-width="18%">
        <template #default="{ row }">{{ formatDateTime(row.lastLoginTime) }}</template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" min-width="18%">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'log', label: '日志', icon: Document }
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

<script setup>
import { Document, Edit } from "@element-plus/icons-vue";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";

defineProps({
  users: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  showRefresh: { type: Boolean, default: true }
});

const emit = defineEmits(["create", "refresh", "status-change", "action", "page-change", "size-change"]);

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
