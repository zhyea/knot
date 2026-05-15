<template>
  <PageSection title="通知策略">
    <el-form :model="pol" label-width="120px" class="send-form">
      <el-form-item label="事件类型"><el-input v-model="pol.eventType" /></el-form-item>
      <el-form-item label="去重窗口"><el-input v-model="pol.dedupWindow" /></el-form-item>
      <el-form-item label="升级阈值"><el-input v-model="pol.escalateAfter" /></el-form-item>
      <el-form-item><el-button :loading="polLoading" @click="doPol">提交</el-button></el-form-item>
    </el-form>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import { createNotifyPolicy } from "../../api/notifications";

const pol = reactive({ eventType: "ALERT", dedupWindow: "5m", escalateAfter: "30m" });
const polLoading = ref(false);

async function doPol() {
  polLoading.value = true;
  try {
    await createNotifyPolicy({
      eventType: pol.eventType,
      dedupWindow: pol.dedupWindow,
      escalateAfter: pol.escalateAfter
    });
    ElMessage.success("已提交（占位接口）");
  } finally {
    polLoading.value = false;
  }
}
</script>

<style scoped>
.send-form {
  max-width: 520px;
}
</style>
