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

<script setup lang="ts">
import type {Dict, Row} from "@/types";
import {ref, watch} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import UserFormDrawer from "../../components/system/UserFormDrawer.vue";
import UserListPanel from "../../components/system/UserListPanel.vue";
import {useListQuery} from "@/composables/useListQuery";
import {listUsers, resetUserPassword, updateUserStatus} from "@/api/users";
import {listUserOperationLogs} from "@/api/operationLogs";

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
const users = ref<Row[]>([]);

const drawerVisible = ref(false);
const editingUser = ref<Dict | null>(null);
const logDrawerVisible = ref(false);
const logUserId = ref<number | string | null>(null);
const logDrawerTitle = ref("操作日志");

function openUserLogs(row: Row) {
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

function openEdit(row: Row) {
  editingUser.value = row;
  drawerVisible.value = true;
}

async function handleResetPassword(row: Row) {
  const result = await resetUserPassword(row.id);
  await ElMessageBox.alert(
    `用户 ${row.username} 的临时密码：${result.oneTimePassword}\n` +
    "请立即转交用户，并提示其登录后修改密码。该密码只在本次展示一次。",
    "密码已重置",
    { confirmButtonText: "我已保存", type: "warning" }
  );
}

async function handleAction(action: string, row: Row) {
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

async function onStatusChange(row: Row, status: string | number | boolean) {
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
