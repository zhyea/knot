<template>
  <PageSection
    title="灰度发布"
    description="创建灰度计划、配置流量阶段与目标对象，支持发布与回滚；步骤 JSON 持久化至后端。"
  >
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建计划</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="targetType" label="目标类型" width="110" />
      <el-table-column prop="targetId" label="目标 ID" width="90" />
      <el-table-column prop="trafficPercent" label="流量%" width="80" />
      <el-table-column label="阶段 steps" min-width="160">
        <template #default="{ row }">{{ (row.steps || []).join(" → ") }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="row.status === 'RUNNING' || row.status === 'ROLLED_BACK'" @click="publish(row)">发布</el-button>
          <el-button link type="warning" @click="rollback(row)">回滚</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dlg" title="新建灰度计划" width="520px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item label="目标类型" required><el-input v-model="form.targetType" placeholder="MODEL / APP" /></el-form-item>
        <el-form-item label="目标 ID" required><el-input-number v-model="form.targetId" :min="1" /></el-form-item>
        <el-form-item label="初始流量%">
          <el-input-number v-model="form.trafficPercent" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="阶段 (%)">
          <el-input v-model="form.stepsStr" placeholder="如 10,30,50,100 逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">创建</el-button>
      </template>
    </el-dialog>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import { listGrayPlans, createGrayPlan, publishGrayPlan, rollbackGrayPlan } from "../api/grayPlans";

const rows = ref([]);
const loading = ref(false);
const dlg = ref(false);
const saving = ref(false);
const form = reactive({
  targetType: "MODEL",
  targetId: 1,
  trafficPercent: 10,
  stepsStr: "10,30,50,100"
});

function parseSteps() {
  return form.stepsStr
    .split(",")
    .map((s) => parseInt(s.trim(), 10))
    .filter((n) => !Number.isNaN(n));
}

async function load() {
  loading.value = true;
  try {
    rows.value = await listGrayPlans();
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  form.targetType = "MODEL";
  form.targetId = 1;
  form.trafficPercent = 10;
  form.stepsStr = "10,30,50,100";
  dlg.value = true;
}

async function submit() {
  if (!form.targetType?.trim() || !form.targetId) {
    ElMessage.warning("请填写目标类型与 ID");
    return;
  }
  const steps = parseSteps();
  if (steps.length === 0) {
    ElMessage.warning("阶段格式不正确");
    return;
  }
  saving.value = true;
  try {
    await createGrayPlan({
      targetType: form.targetType.trim(),
      targetId: form.targetId,
      steps,
      trafficPercent: form.trafficPercent
    });
    ElMessage.success("已创建");
    dlg.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function publish(row) {
  await ElMessageBox.confirm(`确认发布计划 #${row.id}？`, "灰度发布", { type: "warning" });
  await publishGrayPlan(row.id);
  ElMessage.success("已发布");
  await load();
}

async function rollback(row) {
  await ElMessageBox.confirm(`确认回滚计划 #${row.id}？`, "回滚", { type: "warning" });
  await rollbackGrayPlan(row.id);
  ElMessage.success("已回滚");
  await load();
}

load();
</script>
