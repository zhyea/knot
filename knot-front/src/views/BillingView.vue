<template>
  <PageSection
    title="计费规则及成本统计"
    description="维护计费规则、查看网关请求汇总与明细，并发起供应商维度对账（演示逻辑）。"
  >
    <el-tabs v-model="tab">
      <el-tab-pane label="计费规则" name="rules">
        <div class="toolbar">
          <el-button type="primary" @click="openRule">新建规则</el-button>
          <el-button @click="loadRules">刷新</el-button>
        </div>
        <el-table v-loading="rLoading" :data="rules" stripe border size="small">
          <el-table-column prop="code" label="编码" width="120" />
          <el-table-column prop="name" label="名称" min-width="140" />
          <el-table-column prop="unit" label="单位" width="90" />
          <el-table-column prop="unitPrice" label="单价" width="100" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="成本汇总 / 明细" name="usage">
        <el-row :gutter="12" class="stat-row" v-loading="sLoading">
          <el-col :xs="24" :sm="8">
            <el-card shadow="hover"><div class="st">总请求</div><div class="sv">{{ summary?.requestCount ?? "—" }}</div></el-card>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-card shadow="hover"><div class="st">Token 合计</div><div class="sv">{{ summary?.tokenUsage ?? "—" }}</div></el-card>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-card shadow="hover"><div class="st">成本合计</div><div class="sv">{{ fmtMoney(summary?.totalCost) }}</div></el-card>
          </el-col>
        </el-row>
        <div class="toolbar">
          <el-button @click="loadSummary">刷新汇总</el-button>
          <el-button @click="loadDetails">刷新明细</el-button>
        </div>
        <el-table v-loading="dLoading" :data="details" stripe border size="small">
          <el-table-column prop="requestId" label="请求 ID" min-width="140" />
          <el-table-column prop="appId" label="应用" width="90" />
          <el-table-column prop="modelCode" label="模型" width="90" />
          <el-table-column prop="tokenUsage" label="Token" width="90" />
          <el-table-column prop="cost" label="成本" width="100">
            <template #default="{ row }">{{ fmtMoney(row.cost) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="对账" name="reco">
        <el-form :inline="true" :model="reco" class="reco-form">
          <el-form-item label="供应商编码"><el-input v-model="reco.providerCode" placeholder="如 default" /></el-form-item>
          <el-form-item label="账期"><el-input v-model="reco.billDate" placeholder="如 2026-04" /></el-form-item>
          <el-form-item><el-button type="primary" :loading="recoLoading" @click="runReco">执行对账</el-button></el-form-item>
        </el-form>
        <el-descriptions v-if="recoResult" title="结果" :column="2" border>
          <el-descriptions-item label="供应商">{{ recoResult.providerCode }}</el-descriptions-item>
          <el-descriptions-item label="账期">{{ recoResult.billDate }}</el-descriptions-item>
          <el-descriptions-item label="比对行数">{{ recoResult.comparedRows }}</el-descriptions-item>
          <el-descriptions-item label="差异行数">{{ recoResult.diffRows }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ recoResult.status }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="ruleDlg" title="新建计费规则" width="480px" destroy-on-close>
      <el-form :model="ruleForm" label-width="90px">
        <el-form-item label="编码" required><el-input v-model="ruleForm.code" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="ruleForm.name" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="ruleForm.unit" placeholder="1K tokens" /></el-form-item>
        <el-form-item label="单价"><el-input-number v-model="ruleForm.unitPrice" :min="0" :step="0.0001" :precision="6" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDlg = false">取消</el-button>
        <el-button type="primary" :loading="ruleSaving" @click="submitRule">创建</el-button>
      </template>
    </el-dialog>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import { fmtMoney } from "../utils/format";
import {
  listBillingRules,
  createBillingRule,
  getBillingSummary,
  listBillingDetails,
  runReconciliation
} from "../api/billing";

const tab = ref("rules");
const rules = ref([]);
const rLoading = ref(false);
const summary = ref(null);
const sLoading = ref(false);
const details = ref([]);
const dLoading = ref(false);

const ruleDlg = ref(false);
const ruleSaving = ref(false);
const ruleForm = reactive({ code: "", name: "", unit: "1K tokens", unitPrice: 0.002 });

const reco = reactive({ providerCode: "default", billDate: "2026-04" });
const recoLoading = ref(false);
const recoResult = ref(null);

async function loadRules() {
  rLoading.value = true;
  try {
    rules.value = await listBillingRules();
  } finally {
    rLoading.value = false;
  }
}

async function loadSummary() {
  sLoading.value = true;
  try {
    summary.value = await getBillingSummary();
  } finally {
    sLoading.value = false;
  }
}

async function loadDetails() {
  dLoading.value = true;
  try {
    details.value = await listBillingDetails();
  } finally {
    dLoading.value = false;
  }
}

function openRule() {
  ruleForm.code = "";
  ruleForm.name = "";
  ruleForm.unit = "1K tokens";
  ruleForm.unitPrice = 0.002;
  ruleDlg.value = true;
}

async function submitRule() {
  if (!ruleForm.code?.trim() || !ruleForm.name?.trim()) {
    ElMessage.warning("请填写编码与名称");
    return;
  }
  ruleSaving.value = true;
  try {
    await createBillingRule({
      code: ruleForm.code.trim(),
      name: ruleForm.name.trim(),
      unit: ruleForm.unit,
      unitPrice: ruleForm.unitPrice
    });
    ElMessage.success("已创建");
    ruleDlg.value = false;
    await loadRules();
  } finally {
    ruleSaving.value = false;
  }
}

async function runReco() {
  recoLoading.value = true;
  try {
    recoResult.value = await runReconciliation({
      providerCode: reco.providerCode.trim(),
      billDate: reco.billDate.trim()
    });
    ElMessage.success("对账完成");
  } finally {
    recoLoading.value = false;
  }
}

loadRules();
loadSummary();
loadDetails();
</script>

<style scoped>
.st {
  font-size: 13px;
  color: #909399;
}
.sv {
  font-size: 22px;
  font-weight: 600;
  margin-top: 6px;
  color: #303133;
}
.reco-form {
  margin-bottom: 16px;
}
</style>
