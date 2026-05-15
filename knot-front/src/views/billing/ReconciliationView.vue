<template>
  <PageSection title="对账管理">
    <el-form :inline="true" :model="reco" class="reco-form">
      <el-form-item label="供应商编码"><el-input v-model="reco.providerCode" placeholder="如 default" /></el-form-item>
      <el-form-item label="账期"><el-input v-model="reco.billDate" placeholder="如 2026-04" /></el-form-item>
      <el-form-item><el-button type="primary" :loading="loading" @click="runReco">执行对账</el-button></el-form-item>
    </el-form>
    <el-descriptions v-if="result" title="结果" :column="2" border>
      <el-descriptions-item label="供应商">{{ result.providerCode }}</el-descriptions-item>
      <el-descriptions-item label="账期">{{ result.billDate }}</el-descriptions-item>
      <el-descriptions-item label="比对行数">{{ result.comparedRows }}</el-descriptions-item>
      <el-descriptions-item label="差异行数">{{ result.diffRows }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ result.status }}</el-descriptions-item>
    </el-descriptions>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import { runReconciliation } from "../../api/billing";

const reco = reactive({ providerCode: "default", billDate: "2026-04" });
const loading = ref(false);
const result = ref(null);

async function runReco() {
  loading.value = true;
  try {
    result.value = await runReconciliation({
      providerCode: reco.providerCode.trim(),
      billDate: reco.billDate.trim()
    });
    ElMessage.success("对账完成");
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.reco-form {
  margin-bottom: 16px;
}
</style>
