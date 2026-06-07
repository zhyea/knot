<template>
  <div>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="id" label="ID" width="70" align="center" header-align="center" />
      <el-table-column prop="appId" label="App ID" min-width="120" />
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="deptName" label="所属部门" min-width="120" show-overflow-tooltip />
      <el-table-column prop="ownerName" label="负责人" min-width="100" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="150" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'log', label: '日志', icon: Document },
              { key: 'delete', label: '删除', icon: Delete, type: 'danger' }
            ]"
            @action="(action) => handleAction(action, row)"
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
import { ElMessage, ElMessageBox } from "element-plus";
import { Delete, Document, Edit } from "@element-plus/icons-vue";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";
import { deleteApp } from "../../api/apps";

defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  showRefresh: { type: Boolean, default: true }
});

const emit = defineEmits(["edit", "log", "refresh", "page-change", "size-change", "changed"]);

function handleAction(action, row) {
  if (action === "edit") emit("edit", row);
  if (action === "log") emit("log", row);
  if (action === "delete") onDelete(row);
}

async function onDelete(row) {
  await ElMessageBox.confirm(
    `确认删除应用“${row.name}”？\n删除后应用不可恢复；如该应用已配置 API 凭证或模型权限，将无法删除。`,
    "删除应用",
    {
      type: "warning",
      confirmButtonText: "确认删除",
      cancelButtonText: "取消"
    }
  );
  await deleteApp(row.id);
  ElMessage.success(`应用“${row.name}”已删除`);
  emit("changed");
}
</script>
