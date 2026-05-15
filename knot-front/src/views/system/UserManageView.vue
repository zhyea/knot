<template>
  <PageSection title="用户管理">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建用户</el-button>
      <el-button @click="pageLoad">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="users" stripe border >
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
      <el-table-column label="操作" min-width="8%" align="center" header-align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
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

    <!-- 新建/编辑用户抽屉 -->
    <el-drawer v-model="drawerVisible" :title="isEdit ? '编辑用户' : '新建用户'" size="480px" destroy-on-close>
      <el-form :model="userForm" label-width="90px">
        <el-form-item label="用户名">
          <el-input v-model="userForm.username" :disabled="isEdit" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="userForm.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item v-if="isEdit" label="密码">
          <el-input 
            v-model="userForm.password" 
            type="password" 
            placeholder="留空则不修改密码"
            show-password
          />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="密码" required>
          <el-input 
            v-model="userForm.password" 
            type="password" 
            placeholder="请设置密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="userForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
            inline-prompt
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitUser">保存</el-button>
      </template>
    </el-drawer>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import { usePageList } from "../../composables/usePageList";
import { listUsers, createUser, updateUser, updateUserStatus } from "../../api/users";

const { rows, loading, total, pageNum, pageSize, load: pageLoad, onPageChange, onSizeChange, resetPage } = usePageList(listUsers);
const users = ref([]);

// 抽屉相关
const drawerVisible = ref(false);
const isEdit = ref(false);
const saving = ref(false);
const userForm = reactive({ 
  id: null, 
  username: "", 
  realName: "", 
  password: "",
  status: 1 
});

// 监听 rows 变化，同步到 users
import { watch } from "vue";
watch(rows, (list) => {
  users.value = list || [];
}, { immediate: true });

// 格式化日期时间
function formatDateTime(dateTime) {
  if (!dateTime) return '-';
  return new Date(dateTime).toLocaleString('zh-CN', { 
    year: 'numeric', 
    month: '2-digit', 
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
}

// 新建用户
function openCreate() {
  isEdit.value = false;
  userForm.id = null;
  userForm.username = "";
  userForm.realName = "";
  userForm.password = "";
  userForm.status = 1;
  drawerVisible.value = true;
}

// 编辑用户
function openEdit(row) {
  isEdit.value = true;
  userForm.id = row.id;
  userForm.username = row.username;
  userForm.realName = row.realName;
  userForm.password = "";
  userForm.status = row.status;
  drawerVisible.value = true;
}

// 提交用户
async function submitUser() {
  if (!userForm.username?.trim()) {
    ElMessage.warning("请填写用户名");
    return;
  }
  if (!isEdit.value && !userForm.password?.trim()) {
    ElMessage.warning("请设置密码");
    return;
  }
  saving.value = true;
  try {
    const payload = {
      id: userForm.id,
      username: userForm.username.trim(),
      realName: userForm.realName?.trim() || userForm.username.trim(),
      status: userForm.status
    };
    
    // 如果有密码则添加到payload
    if (userForm.password?.trim()) {
      payload.password = userForm.password.trim();
    }
    
    if (isEdit.value) {
      await updateUser(userForm.id, payload);
      ElMessage.success("已保存");
    } else {
      await createUser(payload);
      ElMessage.success("已创建");
    }
    drawerVisible.value = false;
    await resetPage();
  } finally {
    saving.value = false;
  }
}

// 状态切换
async function onStatusChange(row, status) {
  try {
    await updateUserStatus(row.id, { status: status.toString() });
    ElMessage.success("状态已更新");
    await resetPage();
  } catch {
    // 失败时恢复原状态
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
