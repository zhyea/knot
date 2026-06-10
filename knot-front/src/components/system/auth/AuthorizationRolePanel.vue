<template>
  <section class="auth-panel">
    <div class="auth-panel__header">
      <div class="auth-panel__title">角色</div>
      <el-button type="primary" @click="emit('create')">新建角色</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="rows"
      size="small"
      stripe
      border
      highlight-current-row
      row-key="id"
      :current-row-key="selectedRoleId"
      @current-change="onCurrentChange"
    >
      <el-table-column prop="code" label="角色编码" min-width="150" show-overflow-tooltip />
      <el-table-column prop="name" label="角色名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="140" align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'grant', label: '授权', icon: Lock },
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'delete', label: '删除', icon: Delete, type: 'danger', confirm: `确认删除角色 ${row.name} ?` }
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
      :show-refresh="false"
      @page-change="(page) => emit('page-change', page)"
      @size-change="(size) => emit('size-change', size)"
    />
  </section>
</template>

<script setup>
import { Delete, Edit, Lock } from "@element-plus/icons-vue";
import RowActions from "../../common/RowActions.vue";
import ListPagination from "../../common/ListPagination.vue";

const props = defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  selectedRoleId: { type: Number, default: null }
});

const emit = defineEmits(["create", "action", "select", "page-change", "size-change"]);

function onCurrentChange(row) {
  emit("select", row || null);
}
</script>

<style scoped>
.auth-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.auth-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.auth-panel__title {
  font-size: 14px;
  color: #303133;
  font-weight: 600;
}
</style>
