<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按用户名、姓名、部门筛选"
          @query="handleQuery"
        />
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建用户</el-button>
          </div>
        </div>

        <UserListPanel
          :users="users"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :show-refresh="false"
          @status-change="onStatusChange"
          @action="handleAction"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <UserFormDrawer v-model="drawerVisible" :user="editingUser" @saved="resetPage" />

    <OperationLogDrawer
      v-model="logDrawerVisible"
      :title="logDrawerTitle"
      :load-logs="loadUserOperationLogs"
    />
  </PageSection>
</template>

<script setup>
import { reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import UserFormDrawer from "../../components/system/UserFormDrawer.vue";
import UserListPanel from "../../components/system/UserListPanel.vue";
import { useListQuery } from "../../composables/useListQuery";
import { listUsers, resetUserPassword, updateUserStatus } from "../../api/users";
import { listUserOperationLogs } from "../../api/operationLogs";

const {
  query,
  rows,
  loading,
  total,
  pageNum,
  pageSize,
  load: pageLoad,
  onPageChange,
  onSizeChange,
  resetPage,
  handleQuery,
  handleReset
} = useListQuery({ apiFn: listUsers, fields: { keyword: "" } });
const users = ref([]);

const drawerVisible = ref(false);
const editingUser = ref(null);
const logDrawerVisible = ref(false);
const logUserId = ref(null);
const logDrawerTitle = ref("操作日志");

function openUserLogs(row) {
  logUserId.value = row.id;
  logDrawerTitle.value = `用户操作日志 - ${row.username || row.id}`;
  logDrawerVisible.value = true;
}

function loadUserOperationLogs() {
  if (logUserId.value == null) {
    return Promise.resolve([]);
  }
  return listUserOperationLogs(logUserId.value);
}

watch(
  rows,
  (list) => {
    users.value = list || [];
  },
  { immediate: true }
);

function openCreate() {
  editingUser.value = null;
  drawerVisible.value = true;
}

function openEdit(row) {
  editingUser.value = row;
  drawerVisible.value = true;
}

async function handleResetPassword(row) {
  await resetUserPassword(row.id);
  ElMessage.success(`已将用户 ${row.username} 的密码重置为 12345678`);
}

async function handleAction(action, row) {
  if (action === "edit") {
    openEdit(row);
  }
  if (action === "log") {
    openUserLogs(row);
  }
  if (action === "reset-password") {
    await handleResetPassword(row);
  }
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
