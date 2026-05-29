<template>
  <PageSection title="用户管理">
    <UserListPanel
      :users="users"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      @create="openCreate"
      @refresh="pageLoad"
      @status-change="onStatusChange"
      @action="handleAction"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    />

    <UserFormDrawer v-model="drawerVisible" :user="editingUser" @saved="resetPage" />

    <OperationLogDrawer
      v-model="logDrawerVisible"
      :title="logDrawerTitle"
      :load-logs="loadUserOperationLogs"
    />
  </PageSection>
</template>

<script setup>
import { ref, watch } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import UserFormDrawer from "../../components/system/UserFormDrawer.vue";
import UserListPanel from "../../components/system/UserListPanel.vue";
import { usePageList } from "../../composables/usePageList";
import { listUsers, updateUserStatus } from "../../api/users";
import { listUserOperationLogs } from "../../api/operationLogs";

const { rows, loading, total, pageNum, pageSize, load: pageLoad, onPageChange, onSizeChange, resetPage } = usePageList(listUsers);
const users = ref([]);

const drawerVisible = ref(false);
const editingUser = ref(null);

const logDrawerVisible = ref(false);
const logUserId = ref(null);
const logDrawerTitle = ref("操作日志");

function openUserLogs(row) {
  logUserId.value = row.id;
  logDrawerTitle.value = `用户操作日志 — ${row.username || row.id}`;
  logDrawerVisible.value = true;
}

function loadUserOperationLogs() {
  if (logUserId.value == null) {
    return Promise.resolve([]);
  }
  return listUserOperationLogs(logUserId.value);
}

watch(rows, (list) => {
  users.value = list || [];
}, { immediate: true });

function openCreate() {
  editingUser.value = null;
  drawerVisible.value = true;
}

function openEdit(row) {
  editingUser.value = row;
  drawerVisible.value = true;
}

function handleAction(action, row) {
  if (action === "edit") openEdit(row);
  if (action === "log") openUserLogs(row);
}

async function onStatusChange(row, status) {
  try {
    await updateUserStatus(row.id, { status: status.toString() });
    ElMessage.success("状态已更新");
    await resetPage();
  } catch {
    row.status = status === 1 ? 0 : 1;
  }
}

pageLoad();
</script>
