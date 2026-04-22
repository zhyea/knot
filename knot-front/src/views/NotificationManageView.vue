<template>
  <PageSection
    title="通知管理"
    description="维护通知模板并通过演示接口触发发送记录；策略创建为占位回显。"
  >
    <el-tabs v-model="tab">
      <el-tab-pane label="模板" name="tpl">
        <div class="toolbar">
          <el-button type="primary" @click="openTpl">新建模板</el-button>
          <el-button @click="loadTpl">刷新</el-button>
        </div>
        <el-table v-loading="tLoading" :data="templates" stripe border size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="code" label="编码" width="120" />
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="channel" label="渠道" width="90" />
          <el-table-column prop="content" label="内容模板" min-width="160" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="发送" name="send">
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
      </el-tab-pane>
      <el-tab-pane label="策略（占位）" name="pol">
        <el-form :model="pol" label-width="120px" class="send-form">
          <el-form-item label="事件类型"><el-input v-model="pol.eventType" /></el-form-item>
          <el-form-item label="去重窗口"><el-input v-model="pol.dedupWindow" /></el-form-item>
          <el-form-item label="升级阈值"><el-input v-model="pol.escalateAfter" /></el-form-item>
          <el-form-item><el-button :loading="polLoading" @click="doPol">提交</el-button></el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="tplDlg" title="新建模板" width="520px" destroy-on-close>
      <el-form :model="tplForm" label-width="100px">
        <el-form-item label="编码" required><el-input v-model="tplForm.code" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="tplForm.name" /></el-form-item>
        <el-form-item label="渠道"><el-input v-model="tplForm.channel" placeholder="EMAIL" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="tplForm.content" type="textarea" :rows="5" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tplDlg = false">取消</el-button>
        <el-button type="primary" :loading="tplSaving" @click="submitTpl">创建</el-button>
      </template>
    </el-dialog>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import {
  listNotifyTemplates,
  createNotifyTemplate,
  sendNotification,
  createNotifyPolicy
} from "../api/notifications";

const tab = ref("tpl");
const templates = ref([]);
const tLoading = ref(false);
const tplDlg = ref(false);
const tplSaving = ref(false);
const tplForm = reactive({ code: "", name: "", channel: "EMAIL", content: "" });

const send = reactive({ templateCode: "welcome", receivers: "ops@example.com" });
const sendLoading = ref(false);
const sendResult = ref(null);

const pol = reactive({ eventType: "ALERT", dedupWindow: "5m", escalateAfter: "30m" });
const polLoading = ref(false);

async function loadTpl() {
  tLoading.value = true;
  try {
    templates.value = await listNotifyTemplates();
  } finally {
    tLoading.value = false;
  }
}

function openTpl() {
  tplForm.code = "";
  tplForm.name = "";
  tplForm.channel = "EMAIL";
  tplForm.content = "";
  tplDlg.value = true;
}

async function submitTpl() {
  if (!tplForm.code?.trim() || !tplForm.name?.trim()) {
    ElMessage.warning("请填写编码与名称");
    return;
  }
  tplSaving.value = true;
  try {
    await createNotifyTemplate({
      id: null,
      code: tplForm.code.trim(),
      name: tplForm.name.trim(),
      channel: tplForm.channel,
      content: tplForm.content
    });
    ElMessage.success("已创建");
    tplDlg.value = false;
    await loadTpl();
  } finally {
    tplSaving.value = false;
  }
}

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

loadTpl();
</script>

<style scoped>
.send-form {
  max-width: 520px;
}
</style>
