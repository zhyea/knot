<template>
  <PageSection title="消费者">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建消费者</el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="consumerCode" label="消费者编码" min-width="160" show-overflow-tooltip />
      <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="userName" label="用户" min-width="120" show-overflow-tooltip />
      <el-table-column label="API Key" min-width="240">
        <template #default="{ row }">
          <div class="secret-cell">
            <el-input :model-value="row.secretKey" readonly type="password" show-password size="small" />
            <el-button size="small" :icon="CopyDocument" @click="copySecretKey(row.secretKey)" />
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="ruleCount" label="规则数" width="90" align="center" />
      <el-table-column label="启用" width="88" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled !== false"
            :loading="togglingId === row.id"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
            @change="(val) => handleEnabledChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'rotate', label: '重置 Key', icon: Key, type: 'warning', confirm: '重置后旧 API Key 将不可用，确认继续？' }
            ]"
            @action="(action) => handleAction(action, row)"
          />
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

    <el-drawer
      v-model="drawerVisible"
      :title="editingConsumer ? '编辑消费者' : '新建消费者'"
      size="42%"
      destroy-on-close
      @closed="onClosed"
    >
      <el-form :model="form" label-width="110px">
        <el-form-item label="消费者编码" required :error="consumerCodeError">
          <el-input
            v-model="form.consumerCode"
            maxlength="32"
            show-word-limit
            placeholder="最长 32 位"
            @blur="validateConsumerCode"
          />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="用户">
          <el-select v-model="form.userId" placeholder="请选择用户" clearable filterable style="width: 100%">
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="userLabel(user)"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item v-if="editingConsumer?.secretKey" label="API Key">
          <el-input :model-value="editingConsumer.secretKey" readonly type="password" show-password>
            <template #append>
              <el-button @click="copySecretKey(editingConsumer.secretKey)">复制</el-button>
            </template>
          </el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-drawer>
  </PageSection>
</template>

<script setup>
import { reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { CopyDocument, Edit, Key } from "@element-plus/icons-vue";
import PageSection from "../../components/common/PageSection.vue";
import RowActions from "../../components/common/RowActions.vue";
import { usePageList } from "../../composables/usePageList";
import { useEnabledToggle } from "../../composables/useEnabledToggle";
import { listUsers } from "../../api/users";
import {
  checkRoutingConsumerCode,
  createRoutingConsumer,
  listRoutingConsumers,
  rotateRoutingConsumerSecret,
  updateRoutingConsumer
} from "../../api/routing";
import { generateRoutingRuleCode } from "../../utils/routingRule";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listRoutingConsumers);

const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateRoutingConsumer,
  buildPayload: (row, enabled) => ({
    consumerCode: row.consumerCode,
    name: row.name,
    userId: row.userId ?? null,
    enabled
  })
});

const drawerVisible = ref(false);
const editingConsumer = ref(null);
const saving = ref(false);
const consumerCodeError = ref("");
const userOptions = ref([]);

const form = reactive({
  id: null,
  consumerCode: "",
  name: "",
  userId: null,
  enabled: true
});

function userLabel(user) {
  const name = user.realName?.trim() || user.username;
  return name === user.username ? name : `${name}（${user.username}）`;
}

async function loadOptions() {
  const [usersRes] = await Promise.all([
    listUsers({ pageNum: 1, pageSize: 500 })
  ]);
  userOptions.value = Array.isArray(usersRes?.list) ? usersRes.list : Array.isArray(usersRes) ? usersRes : [];
}

function resetForm(row = null) {
  form.id = row?.id ?? null;
  form.consumerCode = row?.consumerCode || generateRoutingRuleCode();
  form.name = row?.name || "";
  form.userId = row?.userId ?? null;
  form.enabled = row?.enabled !== false;
  consumerCodeError.value = "";
}

function openCreate() {
  editingConsumer.value = null;
  resetForm();
  drawerVisible.value = true;
  loadOptions();
}

function openEdit(row) {
  editingConsumer.value = row;
  resetForm(row);
  drawerVisible.value = true;
  loadOptions();
}

function handleAction(action, row) {
  if (action === "edit") openEdit(row);
  if (action === "rotate") rotateSecret(row);
}

function onClosed() {
  editingConsumer.value = null;
  form.id = null;
}

watch(
  () => form.consumerCode,
  () => {
    if (consumerCodeError.value) {
      consumerCodeError.value = "";
    }
  }
);

async function validateConsumerCode() {
  const code = form.consumerCode?.trim();
  if (!code) {
    consumerCodeError.value = "请填写消费者编码";
    return false;
  }
  try {
    const res = await checkRoutingConsumerCode(code, editingConsumer.value ? form.id : null);
    if (res?.available) {
      consumerCodeError.value = "";
      return true;
    }
    consumerCodeError.value = "消费者编码已存在，请更换";
    return false;
  } catch {
    return false;
  }
}

function buildPayload() {
  return {
    consumerCode: form.consumerCode?.trim(),
    name: form.name?.trim(),
    userId: form.userId,
    enabled: form.enabled
  };
}

async function submit() {
  if (!form.name?.trim()) {
    ElMessage.warning("请填写名称");
    return;
  }
  if (!(await validateConsumerCode())) {
    return;
  }
  saving.value = true;
  try {
    if (editingConsumer.value) {
      await updateRoutingConsumer(form.id, buildPayload());
      ElMessage.success("已保存");
    } else {
      await createRoutingConsumer(buildPayload());
      ElMessage.success("已创建");
    }
    drawerVisible.value = false;
    resetPage();
  } finally {
    saving.value = false;
  }
}

async function handleEnabledChange(row, enabled) {
  await onEnabledChange(row, enabled);
  load();
}

async function rotateSecret(row) {
  await rotateRoutingConsumerSecret(row.id);
  ElMessage.success("API Key 已重置");
  load();
}

async function copySecretKey(secretKey) {
  if (!secretKey) return;
  try {
    await navigator.clipboard.writeText(secretKey);
    ElMessage.success("已复制 API Key");
  } catch {
    ElMessage.error("复制失败");
  }
}

load();
</script>

<style scoped>
.secret-cell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}
</style>
