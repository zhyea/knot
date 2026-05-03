<template>
  <PageSection
    title="系统管理"
    description="用户与状态、操作审计日志、网关节点与备份任务；角色列表为静态配置示例。"
  >
    <el-tabs v-model="tab">
      <el-tab-pane label="角色（静态）" name="roles">
        <el-button class="mb" @click="loadRoles">刷新</el-button>
        <el-table v-loading="rLoading" :data="roles" size="small" stripe border>
          <el-table-column prop="code" label="编码" width="140" />
          <el-table-column prop="name" label="名称" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="用户" name="users">
        <div class="toolbar">
          <el-button type="primary" @click="openUser">新建用户</el-button>
          <el-button @click="loadUsers">刷新</el-button>
        </div>
        <el-table v-loading="uLoading" :data="users" stripe border size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="realName" label="姓名" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-select
                v-model="row._nextStatus"
                placeholder="改状态"
                size="small"
                style="width: 100px"
                @change="(v) => onStatus(row, v)"
              >
                <el-option label="ENABLED" value="ENABLED" />
                <el-option label="DISABLED" value="DISABLED" />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="操作日志" name="logs">
        <div class="toolbar">
          <el-button @click="loadLogs">刷新</el-button>
        </div>
        <el-table v-loading="lLoading" :data="logs" stripe border size="small" @row-click="onLogRow">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="moduleCode" label="模块" width="100" />
          <el-table-column prop="actionCode" label="动作" width="100" />
          <el-table-column prop="targetId" label="目标" min-width="100" />
          <el-table-column prop="resultStatus" label="结果" width="90" />
        </el-table>
        <p class="hint">点击行查看变更 JSON 详情</p>
      </el-tab-pane>
      <el-tab-pane label="网关节点" name="nodes">
        <el-button class="mb" @click="loadNodes">刷新</el-button>
        <el-table v-loading="nLoading" :data="nodes" stripe border size="small">
          <el-table-column prop="nodeId" label="节点" />
          <el-table-column prop="host" label="主机" />
          <el-table-column prop="status" label="状态" width="100" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="备份" name="backup">
        <el-space>
          <el-button type="primary" @click="doBackup" :loading="bkLoading">创建备份任务</el-button>
          <el-input v-model="restoreId" placeholder="备份任务 ID 或编码" style="width: 220px" clearable />
          <el-button @click="doRestore" :loading="rsLoading">发起还原</el-button>
        </el-space>
        <el-alert v-if="bkMsg" class="mt" :title="bkMsg" type="info" :closable="false" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="userDlg" title="新建用户" width="440px" destroy-on-close>
      <el-form :model="userForm" label-width="90px">
        <el-form-item label="用户名" required><el-input v-model="userForm.username" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="userForm.realName" /></el-form-item>
        <el-form-item label="状态"><el-input v-model="userForm.status" placeholder="ACTIVE" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDlg = false">取消</el-button>
        <el-button type="primary" :loading="userSaving" @click="submitUser">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawer" title="日志详情" size="480px">
      <el-input v-model="detailText" type="textarea" :rows="18" readonly />
    </el-drawer>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import {
  listSystemRoles,
  listUsers,
  createUser,
  updateUserStatus,
  listOperationLogs,
  getOperationLogDetail,
  listNodes,
  createBackupTask,
  restoreBackup
} from "../api/system";

const tab = ref("users");

const roles = ref([]);
const rLoading = ref(false);
const users = ref([]);
const uLoading = ref(false);
const logs = ref([]);
const lLoading = ref(false);
const nodes = ref([]);
const nLoading = ref(false);

const userDlg = ref(false);
const userSaving = ref(false);
const userForm = reactive({ username: "", realName: "", status: "ENABLED" });

const detailDrawer = ref(false);
const detailText = ref("");

const bkLoading = ref(false);
const rsLoading = ref(false);
const restoreId = ref("");
const bkMsg = ref("");

async function loadRoles() {
  rLoading.value = true;
  try {
    roles.value = await listSystemRoles();
  } finally {
    rLoading.value = false;
  }
}

async function loadUsers() {
  uLoading.value = true;
  try {
    const list = await listUsers();
    users.value = list.map((u) => ({ ...u, _nextStatus: null }));
  } finally {
    uLoading.value = false;
  }
}

async function loadLogs() {
  lLoading.value = true;
  try {
    logs.value = await listOperationLogs();
  } finally {
    lLoading.value = false;
  }
}

async function loadNodes() {
  nLoading.value = true;
  try {
    nodes.value = await listNodes();
  } finally {
    nLoading.value = false;
  }
}

function openUser() {
  userForm.username = "";
  userForm.realName = "";
  userForm.status = "ENABLED";
  userDlg.value = true;
}

async function submitUser() {
  if (!userForm.username?.trim()) {
    ElMessage.warning("请填写用户名");
    return;
  }
  userSaving.value = true;
  try {
    await createUser({
      id: null,
      username: userForm.username.trim(),
      realName: userForm.realName?.trim() || userForm.username.trim(),
      status: userForm.status || "ENABLED"
    });
    ElMessage.success("已创建");
    userDlg.value = false;
    await loadUsers();
  } finally {
    userSaving.value = false;
  }
}

async function onStatus(row, status) {
  if (!status) return;
  try {
    await updateUserStatus(row.id, { status });
    ElMessage.success("状态已更新");
    row._nextStatus = null;
    await loadUsers();
  } catch {
    row._nextStatus = null;
  }
}

async function onLogRow(row) {
  const d = await getOperationLogDetail(row.id);
  detailText.value = JSON.stringify(d, null, 2);
  detailDrawer.value = true;
}

async function doBackup() {
  bkLoading.value = true;
  bkMsg.value = "";
  try {
    const r = await createBackupTask();
    bkMsg.value = `已创建任务：${r.taskId}，状态 ${r.status}`;
    ElMessage.success("备份任务已创建");
  } finally {
    bkLoading.value = false;
  }
}

async function doRestore() {
  if (!restoreId.value?.trim()) {
    ElMessage.warning("请填写备份编码");
    return;
  }
  rsLoading.value = true;
  try {
    const r = await restoreBackup(restoreId.value.trim());
    bkMsg.value = `还原请求：${r.taskId} → ${r.status}`;
    ElMessage.success("已提交还原");
  } finally {
    rsLoading.value = false;
  }
}

loadRoles();
loadUsers();
loadLogs();
loadNodes();
</script>

<style scoped>
.mb {
  margin-bottom: 10px;
}
.mt {
  margin-top: 12px;
}
.hint {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
</style>
