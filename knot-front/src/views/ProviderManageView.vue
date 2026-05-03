<template>
  <PageSection
    title="供应商管理"
    description="维护供应商接入、基础地址、启用状态与频控/额度策略（JSON）。折扣策略当前为内存示例，重启后清空。"
  >
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建供应商</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="type" label="类型" width="100" />
      <el-table-column prop="baseUrl" label="Base URL" min-width="180" show-overflow-tooltip />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <StatusTag :active="row.enabled" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openDiscount(row)">折扣策略</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '编辑供应商' : '新建供应商'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="供应商名称" />
        </el-form-item>
        <el-form-item label="类型">
          <el-input v-model="form.type" placeholder="如 OPENAI / CUSTOM" />
        </el-form-item>
        <el-form-item label="Base URL">
          <el-input v-model="form.baseUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="频控 JSON">
          <el-input v-model="form.rateLimitJson" type="textarea" :rows="3" placeholder='可选，如 {"rpm":60}' />
        </el-form-item>
        <el-form-item label="额度 JSON">
          <el-input v-model="form.quotaJson" type="textarea" :rows="3" placeholder='可选，如 {"dailyTokens":1000000}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="discountDrawer" :title="`折扣策略 — 供应商 #${currentId}`" size="520px" destroy-on-close>
      <div class="toolbar">
        <el-button type="primary" size="small" @click="openDiscountForm()">新增策略</el-button>
        <el-button size="small" @click="loadDiscounts">刷新</el-button>
      </div>
      <el-table :data="discountRows" size="small" border>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="policyName" label="策略名" />
        <el-table-column prop="discountType" label="类型" width="90" />
        <el-table-column prop="discountValue" label="值" width="70" />
        <el-table-column prop="status" label="状态" width="80" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDiscountForm(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-dialog v-model="dForm.visible" :title="dForm.id ? '编辑折扣' : '新增折扣'" width="480px" append-to-body>
        <el-form :model="dForm" label-width="100px">
          <el-form-item label="策略名"><el-input v-model="dForm.policyName" /></el-form-item>
          <el-form-item label="范围类型"><el-input v-model="dForm.scopeType" placeholder="GLOBAL / MODEL" /></el-form-item>
          <el-form-item label="范围 ID"><el-input-number v-model="dForm.scopeRefId" :min="0" /></el-form-item>
          <el-form-item label="折扣类型"><el-input v-model="dForm.discountType" placeholder="RATE" /></el-form-item>
          <el-form-item label="折扣值"><el-input-number v-model="dForm.discountValue" :step="0.01" /></el-form-item>
          <el-form-item label="优先级"><el-input-number v-model="dForm.priority" :min="0" /></el-form-item>
          <el-form-item label="状态"><el-input v-model="dForm.status" placeholder="ACTIVE" /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dForm.visible = false">取消</el-button>
          <el-button type="primary" :loading="dSaving" @click="submitDiscount">保存</el-button>
        </template>
      </el-dialog>
    </el-drawer>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import StatusTag from "../components/common/StatusTag.vue";
import { parsePolicies } from "../utils/format";
import {
  listProviders,
  createProvider,
  updateProvider,
  listDiscountPolicies,
  createDiscountPolicy,
  updateDiscountPolicy
} from "../api/providers";

const rows = ref([]);
const loading = ref(false);
const saving = ref(false);
const dialog = reactive({ visible: false, isEdit: false });
const form = reactive({
  id: null,
  name: "",
  type: "",
  baseUrl: "",
  enabled: true,
  rateLimitJson: "",
  quotaJson: ""
});

const discountDrawer = ref(false);
const currentId = ref(null);
const discountRows = ref([]);
const dSaving = ref(false);
const dForm = reactive({
  visible: false,
  id: null,
  policyName: "",
  scopeType: "GLOBAL",
  scopeRefId: 0,
  discountType: "RATE",
  discountValue: 0.9,
  priority: 10,
  status: "ACTIVE"
});

function policyPayload() {
  const { rateLimitPolicy, quotaPolicy } = parsePolicies(form);
  return {
    id: form.id,
    name: form.name,
    type: form.type,
    baseUrl: form.baseUrl,
    enabled: form.enabled,
    rateLimitPolicy,
    quotaPolicy
  };
}

async function load() {
  loading.value = true;
  try {
    rows.value = await listProviders();
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  dialog.isEdit = false;
  form.id = null;
  form.name = "";
  form.type = "CUSTOM";
  form.baseUrl = "";
  form.enabled = true;
  form.rateLimitJson = "";
  form.quotaJson = "";
  dialog.visible = true;
}

function openEdit(row) {
  dialog.isEdit = true;
  form.id = row.id;
  form.name = row.name;
  form.type = row.type;
  form.baseUrl = row.baseUrl || "";
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
    const payload = policyPayload();
    if (dialog.isEdit) {
      await updateProvider(form.id, payload);
      ElMessage.success("已保存");
    } else {
      await createProvider(payload);
      ElMessage.success("已创建");
    }
    dialog.visible = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function openDiscount(row) {
  currentId.value = row.id;
  discountDrawer.value = true;
  await loadDiscounts();
}

async function loadDiscounts() {
  if (!currentId.value) return;
  discountRows.value = await listDiscountPolicies(currentId.value);
}

function openDiscountForm(row) {
  if (row) {
    dForm.id = row.id;
    dForm.policyName = row.policyName;
    dForm.scopeType = row.scopeType;
    dForm.scopeRefId = row.scopeRefId;
    dForm.discountType = row.discountType;
    dForm.discountValue = row.discountValue;
    dForm.priority = row.priority;
    dForm.status = row.status;
  } else {
    dForm.id = null;
    dForm.policyName = "";
    dForm.scopeType = "GLOBAL";
    dForm.scopeRefId = 0;
    dForm.discountType = "RATE";
    dForm.discountValue = 0.95;
    dForm.priority = 10;
    dForm.status = "ACTIVE";
  }
  dForm.visible = true;
}

async function submitDiscount() {
  dSaving.value = true;
  try {
    const body = {
      id: dForm.id,
      policyName: dForm.policyName,
      scopeType: dForm.scopeType,
      scopeRefId: dForm.scopeRefId,
      discountType: dForm.discountType,
      discountValue: dForm.discountValue,
      priority: dForm.priority,
      status: dForm.status
    };
    if (dForm.id) {
      await updateDiscountPolicy(currentId.value, dForm.id, body);
    } else {
      await createDiscountPolicy(currentId.value, body);
    }
    ElMessage.success("已保存");
    dForm.visible = false;
    await loadDiscounts();
  } finally {
    dSaving.value = false;
  }
}

load();
</script>
