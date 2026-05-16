<template>
  <PageSection title="用户管理">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建用户</el-button>
      <el-button @click="pageLoad">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="users" stripe border>
      <el-table-column prop="id" label="ID" min-width="5%" align="center" header-align="center" />
      <el-table-column prop="username" label="用户名" min-width="12%" />
      <el-table-column prop="realName" label="姓名" min-width="10%" />
      <el-table-column label="状态" min-width="12%">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
            inline-prompt
            @change="(val) => onStatusChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginTime" label="上次登录时间" min-width="18%">
        <template #default="{ row }">
          {{ formatDateTime(row.lastLoginTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" min-width="18%">
        <template #default="{ row }">
          {{ formatDateTime(row.updatedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="14%" align="center" header-align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openUserLogs(row)">日志</el-button>
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
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
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
import { ref, watch } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import UserFormDrawer from "../../components/system/UserFormDrawer.vue";
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

function openCreate() {
  editingUser.value = null;
  drawerVisible.value = true;
}

function openEdit(row) {
  editingUser.value = row;
  drawerVisible.value = true;
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

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
