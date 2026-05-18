<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="emit('create')">新建应用</el-button>
      <el-button @click="emit('refresh')">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="id" label="ID" width="70" align="center" header-align="center" />
      <el-table-column prop="appId" label="App ID" min-width="120" />
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="ownerName" label="负责人" min-width="100" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="240" align="center" header-align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="emit('edit', row)">编辑</el-button>
          <el-button link type="primary" @click="emit('metrics', row)">指标</el-button>
          <el-button link type="primary" @click="emit('log', row)">日志</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
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
        @current-change="(p) => emit('page-change', p)"
        @size-change="(s) => emit('size-change', s)"
      />
    </div>
  </div>
</template>

<script setup>
import { ElMessage, ElMessageBox } from "element-plus";
import { deleteApp } from "../../api/apps";

defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 }
});

const emit = defineEmits(["create", "refresh", "edit", "metrics", "log", "page-change", "size-change", "changed"]);

async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除应用「${row.name}」？`, "删除确认", { type: "warning" });
  await deleteApp(row.id);
  ElMessage.success("已删除");
  emit("changed");
}
</script>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
