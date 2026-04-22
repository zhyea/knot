<template>
  <PageSection
    title="安全与监控管理"
    description="查看安全总览、维护策略 JSON、浏览告警，并手动淘汰缓存键（写入 cache_records）。"
  >
    <el-row :gutter="12" class="stat-row" v-loading="ovLoading">
      <el-col :xs="24" :sm="6" v-for="c in overviewCards" :key="c.k">
        <el-card shadow="hover">
          <div class="st">{{ c.label }}</div>
          <div class="sv">{{ c.value }}</div>
        </el-card>
      </el-col>
    </el-row>
    <div class="toolbar">
      <el-button @click="loadOverview">刷新总览</el-button>
      <el-button @click="loadAlerts">刷新告警</el-button>
    </div>

    <el-card class="block" shadow="never">
      <template #header><span class="h">策略更新</span></template>
      <el-form :model="policy" label-width="110px" class="policy-form">
        <el-form-item label="策略编码"><el-input v-model="policy.policyCode" placeholder="如 default-auth" /></el-form-item>
        <el-form-item label="状态"><el-input v-model="policy.status" placeholder="ACTIVE" /></el-form-item>
        <el-form-item label="配置 JSON">
          <el-input v-model="policy.configJson" type="textarea" :rows="6" placeholder='{"mfa":true}' />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="polSaving" @click="savePolicy">保存策略</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="block" shadow="never">
      <template #header><span class="h">告警列表</span></template>
      <el-table :data="alerts" size="small" stripe border>
        <el-table-column prop="alertId" label="告警 ID" width="120" />
        <el-table-column prop="level" label="级别" width="80" />
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column prop="status" label="状态" width="90" />
      </el-table>
    </el-card>

    <el-card class="block" shadow="never">
      <template #header><span class="h">缓存淘汰</span></template>
      <el-form :inline="true" @submit.prevent="runEvict">
        <el-form-item label="Cache Key"><el-input v-model="evict.cacheKey" placeholder="路由/策略键" clearable /></el-form-item>
        <el-form-item label="类型"><el-input v-model="evict.cacheType" placeholder="默认 GENERIC" clearable /></el-form-item>
        <el-form-item><el-button type="warning" :loading="evictLoading" @click="runEvict">淘汰</el-button></el-form-item>
      </el-form>
      <el-alert v-if="evictResult" type="success" :closable="false" show-icon>
        键 {{ evictResult.cacheKey }} → {{ evictResult.result }}
      </el-alert>
    </el-card>
  </PageSection>
</template>

<script setup>
import { computed, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import {
  getSecurityOverview,
  updateSecurityPolicy,
  listSecurityAlerts,
  evictCache
} from "../api/security";

const ov = ref(null);
const ovLoading = ref(false);
const overviewCards = computed(() => {
  const o = ov.value;
  if (!o) {
    return [
      { k: "a", label: "认证启用", value: "—" },
      { k: "s", label: "签名校验", value: "—" },
      { k: "b", label: "封禁 IP 数", value: "—" },
      { k: "c", label: "开放告警", value: "—" },
      { k: "h", label: "缓存命中率%", value: "—" }
    ];
  }
  return [
    { k: "a", label: "认证启用", value: o.authEnabled ? "是" : "否" },
    { k: "s", label: "签名校验", value: o.signVerificationEnabled ? "是" : "否" },
    { k: "b", label: "封禁 IP 数", value: o.blockedIpCount },
    { k: "c", label: "开放告警", value: o.alertCount },
    { k: "h", label: "缓存命中率%", value: o.cacheHitRate }
  ];
});

const policy = reactive({
  policyCode: "default-security",
  status: "ACTIVE",
  configJson: "{}"
});
const polSaving = ref(false);

const alerts = ref([]);

const evict = reactive({ cacheKey: "", cacheType: "" });
const evictLoading = ref(false);
const evictResult = ref(null);

async function loadOverview() {
  ovLoading.value = true;
  try {
    ov.value = await getSecurityOverview();
  } finally {
    ovLoading.value = false;
  }
}

async function loadAlerts() {
  alerts.value = await listSecurityAlerts();
}

async function savePolicy() {
  if (!policy.policyCode?.trim()) {
    ElMessage.warning("请填写策略编码");
    return;
  }
  polSaving.value = true;
  try {
    await updateSecurityPolicy({
      policyCode: policy.policyCode.trim(),
      configJson: policy.configJson || "{}",
      status: policy.status || "ACTIVE"
    });
    ElMessage.success("已保存");
  } finally {
    polSaving.value = false;
  }
}

async function runEvict() {
  if (!evict.cacheKey?.trim()) {
    ElMessage.warning("请填写 cacheKey");
    return;
  }
  evictLoading.value = true;
  try {
    evictResult.value = await evictCache({
      cacheKey: evict.cacheKey.trim(),
      cacheType: evict.cacheType?.trim() || null
    });
    ElMessage.success("已提交淘汰");
    await loadOverview();
  } finally {
    evictLoading.value = false;
  }
}

loadOverview();
loadAlerts();
</script>

<style scoped>
.stat-row {
  margin-bottom: 8px;
}
.st {
  font-size: 13px;
  color: #909399;
}
.sv {
  font-size: 20px;
  font-weight: 600;
  margin-top: 4px;
}
.block {
  margin-top: 16px;
}
.h {
  font-weight: 600;
}
.policy-form {
  max-width: 720px;
}
</style>
