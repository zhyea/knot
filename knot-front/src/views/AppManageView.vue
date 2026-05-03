<template>
  <PageSection
    title="应用管理"
    description="管理应用标识、负责人用户 ID、启用状态与频控/额度策略；可查看演示调用指标。"
  >
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建应用</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="appId" label="App ID" min-width="120" />
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="owner" label="负责人" width="100" />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <StatusTag :active="row.enabled" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openQuota(row)">改配额</el-button>
          <el-button link type="primary" @click="openMetrics(row)">指标</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '编辑应用' : '新建应用'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="App ID" required><el-input v-model="form.appId" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.owner" placeholder="用户 ID 数字字符串" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        <el-form-item label="频控 JSON"><el-input v-model="form.rateLimitJson" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="额度 JSON"><el-input v-model="form.quotaJson" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="quotaDlg" title="更新配额策略" width="480px" destroy-on-close>
      <el-input v-model="quotaJson" type="textarea" :rows="8" placeholder="JSON，如 {&quot;dailyTokens&quot;: 100000}" />
      <template #footer>
        <el-button @click="quotaDlg = false">取消</el-button>
        <el-button type="primary" :loading="quotaSaving" @click="submitQuota">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="metricsDrawer" title="调用指标（演示）" size="400px" v-loading="metricsLoading">
      <el-descriptions v-if="metrics" :column="1" border>
        <el-descriptions-item label="总请求">{{ metrics.totalRequests }}</el-descriptions-item>
        <el-descriptions-item label="成功">{{ metrics.successRequests }}</el-descriptions-item>
        <el-descriptions-item label="失败">{{ metrics.failedRequests }}</el-descriptions-item>
        <el-descriptions-item label="Token 用量">{{ metrics.tokenUsage }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import StatusTag from "../components/common/StatusTag.vue";
import { parseJson, parsePolicies } from "../utils/format";
import { listApps, createApp, updateApp, updateAppQuota, getAppMetrics } from "../api/apps";

const rows = ref([]);
const loading = ref(false);
const saving = ref(false);
const dialog = reactive({ visible: false, isEdit: false });
const form = reactive({
  id: null,
  appId: "",
  name: "",
  owner: "0",
  enabled: true,
  rateLimitJson: "",
  quotaJson: ""
});

const quotaDlg = ref(false);
const quotaSaving = ref(false);
const quotaAppId = ref(null);
const quotaJson = ref("{}");

const metricsDrawer = ref(false);
const metricsLoading = ref(false);
const metrics = ref(null);

function buildPayload() {
  const { rateLimitPolicy, quotaPolicy } = parsePolicies(form);
  return {
    id: form.id,
    appId: form.appId,
    name: form.name,
    owner: form.owner,
    enabled: form.enabled,
    rateLimitPolicy,
    quotaPolicy
  };
}

async function load() {
  loading.value = true;
  try {
    rows.value = await listApps();
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  dialog.isEdit = false;
  form.id = null;
  form.appId = "";
  form.name = "";
  form.owner = "0";
  form.enabled = true;
  form.rateLimitJson = "";
  form.quotaJson = "";
  dialog.visible = true;
}

function openEdit(row) {
  dialog.isEdit = true;
  form.id = row.id;
  form.appId = row.appId;
  form.name = row.name;
  form.owner = row.owner || "0";
  form.enabled = !!row.enabled;
  form.rateLimitJson = row.rateLimitPolicy ? JSON.stringify(row.rateLimitPolicy, null, 2) : "";
  form.quotaJson = row.quotaPolicy ? JSON.stringify(row.quotaPolicy, null, 2) : "";
  dialog.visible = true;
}

async function submitForm() {
  if (!form.appId?.trim() || !form.name?.trim()) {
    ElMessage.warning("请填写 App ID 与名称");
    return;
  }
  saving.value = true;
  try {
    const p = buildPayload();
    if (dialog.isEdit) {
      await updateApp(form.id, p);
      ElMessage.success("已保存");
    } else {
      await createApp(p);
      ElMessage.success("已创建");
    }
    dialog.visible = false;
    await load();
  } finally {
    saving.value = false;
  }
}

function openQuota(row) {
  quotaAppId.value = row.id;
  quotaJson.value = row.quotaPolicy ? JSON.stringify(row.quotaPolicy, null, 2) : "{}";
  quotaDlg.value = true;
}

async function submitQuota() {
  const obj = parseJson(quotaJson.value, null);
  if (obj === null) {
    ElMessage.warning("JSON 格式不正确");
    return;
  }
  quotaSaving.value = true;
  try {
    await updateAppQuota(quotaAppId.value, obj);
    ElMessage.success("已更新配额");
    quotaDlg.value = false;
    await load();
  } finally {
    quotaSaving.value = false;
  }
}

async function openMetrics(row) {
  metricsLoading.value = true;
  try {
    metrics.value = await getAppMetrics(row.id);
    metricsDrawer.value = true;
  } finally {
    metricsLoading.value = false;
  }
}

load();
</script>
