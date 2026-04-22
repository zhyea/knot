<template>
  <PageSection
    title="模型路由规则"
    description="配置路由名称、策略、条件表达式、目标供应商/模型与优先级；下方可查看近期路由切换日志。"
  >
    <el-tabs v-model="tab">
      <el-tab-pane label="规则列表" name="rules">
        <div class="toolbar">
          <el-button type="primary" @click="openCreate">新建规则</el-button>
          <el-button @click="loadRules">刷新</el-button>
        </div>
        <el-table v-loading="loading" :data="rules" stripe border>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="名称" min-width="120" />
          <el-table-column prop="strategy" label="策略" width="110" />
          <el-table-column prop="conditionExpr" label="条件" min-width="140" show-overflow-tooltip />
          <el-table-column prop="targetProviderId" label="供应商" width="90" />
          <el-table-column prop="targetModelId" label="模型" width="80" />
          <el-table-column prop="priority" label="优先级" width="80" />
          <el-table-column label="启用" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? "是" : "否" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="openTest(row)">测试</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="切换日志" name="logs">
        <div class="toolbar">
          <el-button @click="loadLogs">刷新</el-button>
        </div>
        <el-table v-loading="logLoading" :data="logs" stripe border size="small">
          <el-table-column prop="reason" label="原因" min-width="120" />
          <el-table-column prop="fromTarget" label="从" min-width="100" />
          <el-table-column prop="toTarget" label="到" min-width="100" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '编辑路由规则' : '新建路由规则'" width="620px" destroy-on-close>
      <el-form :model="form" label-width="120px">
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="策略类型"><el-input v-model="form.strategy" placeholder="FIXED / WEIGHTED" /></el-form-item>
        <el-form-item label="条件表达式"><el-input v-model="form.conditionExpr" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="目标供应商 ID"><el-input-number v-model="form.targetProviderId" :min="0" /></el-form-item>
        <el-form-item label="目标模型 ID"><el-input-number v-model="form.targetModelId" :min="0" /></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="form.priority" :min="0" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testDlg" title="路由规则测试" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="App ID"><el-input v-model="testForm.appId" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="tagsStr" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="时间"><el-input v-model="testForm.time" placeholder="可选" /></el-form-item>
      </el-form>
      <el-alert v-if="testResult" type="success" :closable="false" class="mt">
        匹配规则 #{{ testResult.matchedRuleId }} → 供应商 {{ testResult.targetProviderId }} / 模型 {{ testResult.targetModelId }}（{{ testResult.status }}）
      </el-alert>
      <template #footer>
        <el-button @click="testDlg = false">关闭</el-button>
        <el-button type="primary" :loading="testLoading" @click="runTest">执行</el-button>
      </template>
    </el-dialog>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import {
  listRoutingRules,
  createRoutingRule,
  updateRoutingRule,
  testRoutingRule,
  listSwitchLogs
} from "../api/routing";

const tab = ref("rules");
const rules = ref([]);
const logs = ref([]);
const loading = ref(false);
const logLoading = ref(false);
const saving = ref(false);
const dialog = reactive({ visible: false, isEdit: false });
const form = reactive({
  id: null,
  name: "",
  strategy: "FIXED",
  conditionExpr: "true",
  targetProviderId: 0,
  targetModelId: 0,
  priority: 100,
  enabled: true
});

const testDlg = ref(false);
const testLoading = ref(false);
const testId = ref(null);
const testForm = reactive({ appId: "demo-app", time: "" });
const tagsStr = ref("");
const testResult = ref(null);

async function loadRules() {
  loading.value = true;
  try {
    rules.value = await listRoutingRules();
  } finally {
    loading.value = false;
  }
}

async function loadLogs() {
  logLoading.value = true;
  try {
    logs.value = await listSwitchLogs();
  } finally {
    logLoading.value = false;
  }
}

function openCreate() {
  dialog.isEdit = false;
  form.id = null;
  form.name = "";
  form.strategy = "FIXED";
  form.conditionExpr = "true";
  form.targetProviderId = 0;
  form.targetModelId = 0;
  form.priority = 100;
  form.enabled = true;
  dialog.visible = true;
}

function openEdit(row) {
  dialog.isEdit = true;
  form.id = row.id;
  form.name = row.name;
  form.strategy = row.strategy;
  form.conditionExpr = row.conditionExpr || "";
  form.targetProviderId = row.targetProviderId;
  form.targetModelId = row.targetModelId;
  form.priority = row.priority;
  form.enabled = !!row.enabled;
  dialog.visible = true;
}

async function submitForm() {
  if (!form.name?.trim()) {
    ElMessage.warning("请填写名称");
    return;
  }
  saving.value = true;
  try {
    const body = {
      id: form.id,
      name: form.name,
      strategy: form.strategy,
      conditionExpr: form.conditionExpr,
      targetProviderId: form.targetProviderId,
      targetModelId: form.targetModelId,
      priority: form.priority,
      enabled: form.enabled
    };
    if (dialog.isEdit) {
      await updateRoutingRule(form.id, body);
      ElMessage.success("已保存");
    } else {
      await createRoutingRule(body);
      ElMessage.success("已创建");
    }
    dialog.visible = false;
    await loadRules();
  } finally {
    saving.value = false;
  }
}

function openTest(row) {
  testId.value = row.id;
  testResult.value = null;
  testForm.appId = "demo-app";
  testForm.time = "";
  tagsStr.value = "";
  testDlg.value = true;
}

async function runTest() {
  testLoading.value = true;
  try {
    const tags = tagsStr.value
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    testResult.value = await testRoutingRule(testId.value, {
      appId: testForm.appId,
      tags,
      time: testForm.time || null
    });
  } finally {
    testLoading.value = false;
  }
}

loadRules();
loadLogs();
</script>

<style scoped>
.mt {
  margin-top: 12px;
}
</style>
