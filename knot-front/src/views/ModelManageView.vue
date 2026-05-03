<template>
  <PageSection
    title="模型管理"
    description="维护模型与供应商关联、版本、类型及启用状态；支持联调测试与版本切换（演示数据）。"
  >
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建模型</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="providerId" label="供应商 ID" width="110" />
      <el-table-column prop="modelType" label="类型" width="100" />
      <el-table-column prop="version" label="版本" width="100" />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <StatusTag :active="row.enabled" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openTest(row)">测试</el-button>
          <el-button link type="primary" @click="openSwitch(row)">切版本</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '编辑模型' : '新建模型'" width="580px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item v-if="!dialog.isEdit" label="模型编码">
          <el-input v-model="form.modelCode" placeholder="可选，留空自动生成" />
        </el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="供应商 ID" required><el-input-number v-model="form.providerId" :min="1" /></el-form-item>
        <el-form-item label="模型类型"><el-input v-model="form.modelType" placeholder="CHAT / EMBED" /></el-form-item>
        <el-form-item label="版本"><el-input v-model="form.version" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        <el-form-item label="频控 JSON"><el-input v-model="form.rateLimitJson" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="额度 JSON"><el-input v-model="form.quotaJson" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testDlg" title="模型联调测试" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="Prompt"><el-input v-model="testForm.prompt" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="温度"><el-input-number v-model="testForm.temperature" :step="0.1" :min="0" :max="2" /></el-form-item>
        <el-form-item label="Max tokens"><el-input-number v-model="testForm.maxTokens" :min="1" /></el-form-item>
      </el-form>
      <el-alert v-if="testResult" type="success" :closable="false" class="mt">
        <template #title>输出（演示）</template>
        <div>{{ testResult.output }}</div>
        <div class="sub">延迟 {{ testResult.latencyMs }} ms · token {{ testResult.tokenUsage }}</div>
      </el-alert>
      <template #footer>
        <el-button @click="testDlg = false">关闭</el-button>
        <el-button type="primary" :loading="testLoading" @click="runTest">发送</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="swDlg" title="切换活跃版本" width="400px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="目标版本"><el-input v-model="swVersion" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="swDlg = false">取消</el-button>
        <el-button type="primary" :loading="swLoading" @click="runSwitch">确定</el-button>
      </template>
    </el-dialog>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import StatusTag from "../components/common/StatusTag.vue";
import { parsePolicies } from "../utils/format";
import { listModels, createModel, updateModel, testModel, switchModelVersion } from "../api/models";

const rows = ref([]);
const loading = ref(false);
const saving = ref(false);
const dialog = reactive({ visible: false, isEdit: false });
const form = reactive({
  id: null,
  modelCode: "",
  name: "",
  providerId: 1,
  modelType: "CHAT",
  version: "1.0.0",
  enabled: true,
  rateLimitJson: "",
  quotaJson: ""
});

const testDlg = ref(false);
const testLoading = ref(false);
const testId = ref(null);
const testForm = reactive({ prompt: "hello", temperature: 0.7, maxTokens: 256 });
const testResult = ref(null);

const swDlg = ref(false);
const swLoading = ref(false);
const swId = ref(null);
const swVersion = ref("");

function payload() {
  const { rateLimitPolicy, quotaPolicy } = parsePolicies(form);
  return {
    id: form.id,
    modelCode: form.modelCode || null,
    name: form.name,
    providerId: form.providerId,
    modelType: form.modelType,
    version: form.version,
    enabled: form.enabled,
    rateLimitPolicy,
    quotaPolicy
  };
}

async function load() {
  loading.value = true;
  try {
    rows.value = await listModels();
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  dialog.isEdit = false;
  form.id = null;
  form.modelCode = "";
  form.name = "";
  form.providerId = 1;
  form.modelType = "CHAT";
  form.version = "1.0.0";
  form.enabled = true;
  form.rateLimitJson = "";
  form.quotaJson = "";
  dialog.visible = true;
}

function openEdit(row) {
  dialog.isEdit = true;
  form.id = row.id;
  form.modelCode = row.modelCode || "";
  form.name = row.name;
  form.providerId = row.providerId;
  form.modelType = row.modelType;
  form.version = row.version;
  form.enabled = !!row.enabled;
  form.rateLimitJson = row.rateLimitPolicy ? JSON.stringify(row.rateLimitPolicy, null, 2) : "";
  form.quotaJson = row.quotaPolicy ? JSON.stringify(row.quotaPolicy, null, 2) : "";
  dialog.visible = true;
}

async function submitForm() {
  if (!form.name?.trim()) {
    ElMessage.warning("请填写名称");
    return;
  }
  saving.value = true;
  try {
    const p = payload();
    if (dialog.isEdit) {
      await updateModel(form.id, p);
      ElMessage.success("已保存");
    } else {
      await createModel(p);
      ElMessage.success("已创建");
    }
    dialog.visible = false;
    await load();
  } finally {
    saving.value = false;
  }
}

function openTest(row) {
  testId.value = row.id;
  testResult.value = null;
  testDlg.value = true;
}

async function runTest() {
  testLoading.value = true;
  try {
    testResult.value = await testModel(testId.value, {
      prompt: testForm.prompt,
      temperature: testForm.temperature,
      maxTokens: testForm.maxTokens
    });
  } finally {
    testLoading.value = false;
  }
}

function openSwitch(row) {
  swId.value = row.id;
  swVersion.value = row.version || "";
  swDlg.value = true;
}

async function runSwitch() {
  if (!swVersion.value?.trim()) {
    ElMessage.warning("请填写版本号");
    return;
  }
  swLoading.value = true;
  try {
    await switchModelVersion(swId.value, { targetVersion: swVersion.value.trim() });
    ElMessage.success("已切换");
    swDlg.value = false;
    await load();
  } finally {
    swLoading.value = false;
  }
}

load();
</script>

<style scoped>
.mt {
  margin-top: 12px;
}
.sub {
  margin-top: 6px;
  font-size: 12px;
  color: #606266;
}
</style>
