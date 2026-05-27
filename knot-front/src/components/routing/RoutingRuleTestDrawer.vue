<template>
  <el-drawer
    :model-value="modelValue"
    :title="drawerTitle"
    size="50%"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >
    <el-form label-width="96px" class="test-form">
      <el-form-item label="API Key" required>
        <el-input
          v-model="testForm.secretKey"
          placeholder="sk- 开头的消费者 API Key"
          type="password"
          show-password
        />
      </el-form-item>
      <el-form-item label="模型">
        <el-select
          v-model="testForm.model"
          placeholder="默认使用主模型"
          clearable
          filterable
          style="width: 100%"
        >
          <el-option
            v-for="m in modelOptions"
            :key="m.modelId"
            :label="modelLabel(m)"
            :value="m.modelCode"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="提示词">
        <el-input v-model="testForm.prompt" placeholder="发送给模型的用户消息" clearable />
      </el-form-item>
    </el-form>

    <section class="test-section">
      <div class="section-head">
        <span class="section-title">curl 命令</span>
        <span class="section-hint">随表单参数实时预览</span>
      </div>
      <ShellCodeBlock :code="displayCurl" />
    </section>

    <div class="test-actions">
      <el-button type="primary" :loading="loading" :disabled="!ruleId" @click="runTest">
        执行
      </el-button>
    </div>

    <section v-if="showResultPanel" class="test-section result-section">
      <div class="section-head">
        <span class="section-title">执行结果</span>
        <el-tag v-if="testResult" :type="testResult.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
          {{ testResult.status }}
          <template v-if="testResult.httpStatus != null"> · HTTP {{ testResult.httpStatus }}</template>
        </el-tag>
      </div>
      <el-skeleton v-if="loading" :rows="4" animated />
      <template v-else-if="testResult">
        <div v-if="testResult.modelCode || testResult.errorMessage" class="result-meta">
          <span v-if="testResult.modelCode">模型：{{ testResult.modelCode }}</span>
          <span v-if="testResult.errorMessage" class="result-error">{{ testResult.errorMessage }}</span>
        </div>
        <ShellCodeBlock
          v-if="resultBodyText"
          :code="resultBodyText"
          language="json"
          :copyable="true"
        />
        <el-empty v-else description="无响应内容" :image-size="64" />
      </template>
    </section>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import ShellCodeBlock from "../common/ShellCodeBlock.vue";
import { testRoutingRule } from "../../api/routing";

const GATEWAY_BASE_URL = import.meta.env.VITE_GATEWAY_BASE_URL || "http://127.0.0.1:9090";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  ruleId: { type: Number, default: null },
  ruleName: { type: String, default: "" },
  secretKey: { type: String, default: "" },
  models: { type: Array, default: () => [] }
});

const emit = defineEmits(["update:modelValue"]);

const loading = ref(false);
const hasExecuted = ref(false);
const testForm = reactive({
  secretKey: "",
  prompt: "你好，这是一条路由规则测试消息",
  model: ""
});
const testResult = ref(null);

const drawerTitle = computed(() => {
  const name = props.ruleName?.trim();
  return name ? `路由规则测试 — ${name}` : "路由规则测试";
});

const modelOptions = computed(() => {
  const list = Array.isArray(props.models) ? props.models : [];
  return list.filter((m) => m.modelCode);
});

const resolvedModel = computed(() => {
  if (testForm.model?.trim()) {
    return testForm.model.trim();
  }
  const primary = modelOptions.value.find((m) => m.primary) || modelOptions.value[0];
  return primary?.modelCode || "model-name";
});

const curlPreview = computed(() => buildCurlCommand());

const displayCurl = computed(() => testResult.value?.curl || curlPreview.value);

const showResultPanel = computed(() => hasExecuted.value || loading.value);

const resultBodyText = computed(() => {
  if (!testResult.value?.responseBody) {
    return "";
  }
  return formatBody(testResult.value.responseBody);
});

watch(
  () => [props.modelValue, props.ruleId, props.secretKey],
  ([visible]) => {
    if (visible) {
      testResult.value = null;
      hasExecuted.value = false;
      testForm.secretKey = props.secretKey || "";
      testForm.prompt = "你好，这是一条路由规则测试消息";
      const primary = modelOptions.value.find((m) => m.primary) || modelOptions.value[0];
      testForm.model = primary?.modelCode || "";
    }
  }
);

function modelLabel(m) {
  const name = m.modelName || m.modelCode || `#${m.modelId}`;
  return m.primary ? `${name}（主）` : name;
}

function onClosed() {
  testResult.value = null;
  hasExecuted.value = false;
}

function normalizeBaseUrl() {
  const base = GATEWAY_BASE_URL.trim();
  return base.endsWith("/") ? base.slice(0, -1) : base;
}

function buildCurlCommand() {
  const secretKey = testForm.secretKey?.trim() || "sk-your-routing-secret-key";
  const prompt = testForm.prompt?.trim() || "你好，这是一条路由规则测试消息";
  const body = {
    model: resolvedModel.value,
    messages: [{ role: "user", content: prompt }]
  };
  const json = JSON.stringify(body);
  const escapedJson = json.replace(/'/g, "'\\''");
  const base = normalizeBaseUrl();
  return (
    `curl -X POST '${base}/v1/chat/completions' \\\n` +
    `  -H 'Authorization: Bearer ${secretKey}' \\\n` +
    `  -H 'Content-Type: application/json' \\\n` +
    `  -d '${escapedJson}'`
  );
}

function formatBody(body) {
  if (!body) return "";
  try {
    return JSON.stringify(JSON.parse(body), null, 2);
  } catch {
    return body;
  }
}

async function runTest() {
  if (!props.ruleId) return;
  if (!testForm.secretKey?.trim()) {
    ElMessage.warning("请填写 API Key");
    return;
  }
  hasExecuted.value = true;
  loading.value = true;
  testResult.value = null;
  try {
    testResult.value = await testRoutingRule(props.ruleId, {
      secretKey: testForm.secretKey.trim(),
      prompt: testForm.prompt?.trim() || null,
      model: testForm.model?.trim() || null
    });
    if (testResult.value?.status === "SUCCESS") {
      ElMessage.success("执行成功");
    } else {
      ElMessage.warning("执行未成功，请查看下方结果");
    }
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.test-form {
  margin-bottom: 8px;
}

.test-section {
  margin-top: 20px;
}

.section-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.section-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.test-actions {
  margin-top: 20px;
  display: flex;
  justify-content: flex-start;
}

.result-section {
  min-height: 120px;
}

.result-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.result-error {
  color: var(--el-color-danger);
}
</style>
