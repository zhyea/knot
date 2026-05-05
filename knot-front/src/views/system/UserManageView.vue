<template>
  <PageSection title="用户管理" description="维护系统用户、切换启用/禁用状态。">
    <div class="toolbar">
      <el-button type="primary" @click="openUser">新建用户</el-button>
      <el-button @click="pageLoad">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="users" stripe border size="small">
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

    <el-dialog v-model="userDlg" title="新建用户" width="440px" destroy-on-close>
      <el-form :model="userForm" label-width="90px">
        <el-form-item label="用户名" required><el-input v-model="userForm.username" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="userForm.realName" /></el-form-item>
        <el-form-item label="状态"><el-input v-model="userForm.status" placeholder="ENABLED" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitUser">创建</el-button>
      </template>
    </el-dialog>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import { usePageList } from "../../composables/usePageList";
import { listUsers, createUser, updateUserStatus } from "../../api/users";

const { rows, loading, total, pageNum, pageSize, load: pageLoad, onPageChange, onSizeChange, resetPage } = usePageList(listUsers);
const users = ref([]);
const userDlg = ref(false);
const saving = ref(false);
const userForm = reactive({ username: "", realName: "", status: "ENABLED" });

// 监听 rows 变化，同步到 users（附加 _nextStatus）
import { watch } from "vue";
watch(rows, (list) => {
  users.value = list.map((u) => ({ ...u, _nextStatus: null }));
}, { immediate: true });

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
  saving.value = true;
  try {
    await createUser({
      id: null,
      username: userForm.username.trim(),
      realName: userForm.realName?.trim() || userForm.username.trim(),
      status: userForm.status || "ENABLED"
    });
    ElMessage.success("已创建");
    userDlg.value = false;
    await resetPage();
  } finally {
    saving.value = false;
  }
}

async function onStatus(row, status) {
  if (!status) return;
  try {
    await updateUserStatus(row.id, { status });
    ElMessage.success("状态已更新");
    row._nextStatus = null;
    await resetPage();
  } catch {
    row._nextStatus = null;
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
