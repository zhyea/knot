<template>
  <PageSection title="备份还原">
    <el-space>
      <el-button type="primary" @click="doBackup" :loading="bkLoading">创建备份任务</el-button>
      <el-input v-model="restoreId" placeholder="备份任务 ID 或编码" style="width: 220px" clearable />
      <el-button @click="doRestore" :loading="rsLoading">发起还原</el-button>
    </el-space>
    <el-alert v-if="bkMsg" class="mt" :title="bkMsg" type="info" :closable="false" />
  </PageSection>
</template>

<script setup>
import { ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import { createBackupTask, restoreBackup } from "../../api/system";

const bkLoading = ref(false);
const rsLoading = ref(false);
const restoreId = ref("");
const bkMsg = ref("");

async function doBackup() {
  bkLoading.value = true;
  bkMsg.value = "";
  try {
    const r = await createBackupTask();
    bkMsg.value = `已创建任务：${r.taskId}，状态 ${r.status}`;
    ElMessage.success("备份任务已创建");
  } finally {
    bkLoading.value = false;
  }
}

async function doRestore() {
  if (!restoreId.value?.trim()) {
    ElMessage.warning("请填写备份编码");
    return;
  }
  rsLoading.value = true;
  try {
    const r = await restoreBackup(restoreId.value.trim());
    bkMsg.value = `还原请求：${r.taskId} → ${r.status}`;
    ElMessage.success("已提交还原");
  } finally {
    rsLoading.value = false;
  }
}
</script>

<style scoped>
.mt {
  margin-top: 12px;
}
</style>
