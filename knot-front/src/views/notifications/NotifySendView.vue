<template>
  <PageSection title="通知发送">
    <el-form :model="send" label-width="100px" class="send-form">
      <el-form-item label="模板编码"><el-input v-model="send.templateCode" /></el-form-item>
      <el-form-item label="接收人"><el-input v-model="send.receivers" placeholder="逗号分隔邮箱/手机" /></el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="sendLoading" @click="doSend">发送</el-button>
      </el-form-item>
    </el-form>
    <el-alert v-if="sendResult" type="success" :closable="false" show-icon>
      任务 {{ sendResult.taskId }} · {{ sendResult.status }} · 接收人数 {{ sendResult.receiverCount }}
    </el-alert>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import { sendNotification } from "../../api/notifications";

const send = reactive({ templateCode: "welcome", receivers: "ops@example.com" });
const sendLoading = ref(false);
const sendResult = ref(null);

async function doSend() {
  const list = send.receivers
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  if (!send.templateCode?.trim() || list.length === 0) {
    ElMessage.warning("请填写模板编码与接收人");
    return;
  }
  sendLoading.value = true;
  try {
    sendResult.value = await sendNotification({
      templateCode: send.templateCode.trim(),
      receivers: list,
      vars: {}
    });
    ElMessage.success("已提交发送");
  } finally {
    sendLoading.value = false;
  }
}
</script>

<style scoped>
.send-form {
  max-width: 520px;
}
</style>
