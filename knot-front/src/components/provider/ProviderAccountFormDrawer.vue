<template>
  <el-drawer
      :model-value="modelValue"
      :title="isEdit ? '编辑供应商账户' : '新建供应商账户'"
      size="50%"
      class="drawer-with-scrollbar"
      destroy-on-close
      @update:model-value="emit('update:modelValue', $event)"
      @closed="onClosed"
  >

    <el-scrollbar max-height="calc(100vh - 140px)">
    <el-form v-loading="detailLoading" :model="form" label-width="110px">
      <div class="slot-body">
        <el-form-item label="编码" required :error="codeError">
          <el-input
              v-model="form.code"
              placeholder="最长 32 位"
              maxlength="32"
              show-word-limit
              :disabled="codeChecking"
              @blur="validateCode"
          />
        </el-form-item>
        <el-form-item label="Base URL">
          <el-input v-model="form.baseUrl" placeholder="https://api.example.com"/>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商" required>
              <el-select
                  v-model="form.providerId"
                  placeholder="请选择供应商"
                  filterable
                  style="width: 100%"
              >
                <el-option
                    v-for="option in providerOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用">
              <el-switch v-model="form.enabled"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="认证类型" required>
          <el-select
              v-model="form.credentialType"
              placeholder="请选择认证类型"
              style="width: 100%"
              @change="handleCredentialTypeChange"
          >
            <el-option
                v-for="option in credentialTypeOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
            v-for="field in requiredCredentialFields"
            :key="field"
            :label="credentialFieldLabels[field] || field"
            required
        >
          <el-input
              v-model="form.authConfig[field]"
              :type="credentialFieldType(field)"
              :rows="field === 'account_json' ? 5 : undefined"
              :show-password="isSecretCredentialField(field) && field !== 'account_json' && isAdmin"
              autocomplete="off"
              :placeholder="`请输入 ${credentialFieldLabels[field] || field}`"
          />
        </el-form-item>
        <el-form-item>
          <KvEditor
              v-model="customAuthConfig"
              class="auth-kv-editor"
              value-secret
              :allow-reveal="isAdmin"
          />
        </el-form-item>
      </div>

      <div class="space-line" />

      <TrafficPolicySection
          class="slot-body"
          v-model:rate-limit="form.rateLimitPolicy"
          v-model:quota="form.quotaPolicy"
      />
    </el-form>
    </el-scrollbar>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>

  </el-drawer>
</template>

<script setup>
import {computed, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import KvEditor from "../common/KvEditor.vue";
import TrafficPolicySection from "../common/TrafficPolicySection.vue";
import {useAuth} from "../../composables/useAuth.js";
import {listProviderProfiles} from "../../api/providerProfiles.js";
import {
  createProvider,
  updateProvider,
  getProvider,
  suggestProviderCode,
  checkProviderCode
} from "../../api/providers.js";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  provider: {type: Object, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const {isAdmin} = useAuth();
const providerOptions = ref([]);
const saving = ref(false);
const codeChecking = ref(false);
const codeError = ref("");
const codeValidated = ref(false);
const detailLoading = ref(false);
const form = reactive({
  id: null,
  providerId: null,
  code: "",
  baseUrl: "",
  enabled: true,
  credentialType: "api-key",
  authConfig: {apiKey: ""},
  rateLimitPolicy: {},
  quotaPolicy: {}
});

const isEdit = computed(() => props.provider != null);

const credentialTypeOptions = [
  {value: "api-key", label: "api-key（ApiKey）"},
  {value: "aws-auth", label: "aws-auth（AWS 认证）"},
  {value: "gemini-auth", label: "gemini-auth（gemini 认证）"},
  {value: "ak-sk", label: "ak-sk（AK&SK）"},
  {value: "custom", label: "custom（自定义）"}
];

const credentialFields = {
  "api-key": ["apiKey"],
  "aws-auth": ["accessKey", "secretKey", "region"],
  "gemini-auth": ["project_id", "region", "account_json"],
  "ak-sk": ["accessKey", "secretKey"],
  custom: []
};

const credentialFieldLabels = {
  apiKey: "apiKey",
  accessKey: "accessKey",
  secretKey: "secretKey",
  region: "region",
  project_id: "project_id",
  account_json: "account_json"
};

const requiredCredentialFields = computed(
    () => credentialFields[form.credentialType] || []
);
const customAuthConfig = ref({});

function defaultAuthConfig() {
  return {apiKey: ""};
}

function normalizeAuthConfig(raw) {
  if (raw && typeof raw === "object" && Object.keys(raw).length > 0) {
    return {...raw};
  }
  return defaultAuthConfig();
}

function normalizeCredentialType(value) {
  if (credentialTypeOptions.some((option) => option.value === value)) {
    return value;
  }
  if (value === "API_KEY") return "api-key";
  if (value === "TOKEN") return "custom";
  return "api-key";
}

function syncCredentialParts() {
  const required = new Set(requiredCredentialFields.value);
  const custom = {};
  Object.entries(form.authConfig || {}).forEach(([key, value]) => {
    if (!required.has(key)) {
      custom[key] = value;
    }
  });
  customAuthConfig.value = custom;
}

function handleCredentialTypeChange() {
  const required = requiredCredentialFields.value;
  const requiredSet = new Set(required);
  const config = {};
  // 新类型的必填字段：保留已填值（重叠字段如 accessKey/secretKey 不丢）
  required.forEach((field) => {
    config[field] = form.authConfig?.[field] ?? customAuthConfig.value[field] ?? "";
  });
  // 仅保留用户在自定义编辑器里显式添加的键，旧类型残留字段一律丢弃
  Object.entries(customAuthConfig.value).forEach(([key, value]) => {
    if (!requiredSet.has(key)) {
      config[key] = value;
    }
  });
  form.authConfig = config;
}

function isSecretCredentialField(field) {
  return ["apiKey", "accessKey", "secretKey", "account_json"].includes(field);
}

function credentialFieldType(field) {
  return field === "account_json"
      ? "textarea"
      : isSecretCredentialField(field) ? "password" : "text";
}

async function loadSuggestedCode() {
  try {
    form.code = (await suggestProviderCode()) || "";
    codeError.value = "";
    codeValidated.value = true;
  } catch {
    form.code = "";
    codeValidated.value = false;
  }
}

function fillFormFromRow(row) {
  form.id = row.id;
  form.providerId = row.providerId;
  form.code = row.code || "";
  form.baseUrl = row.baseUrl || "";
  form.enabled = !!row.enabled;
  form.credentialType = normalizeCredentialType(row.credentialType);
  form.authConfig = normalizeAuthConfig(row.authConfig);
  // 初次加载：把存量 authConfig 里不属于当前类型的键拆到自定义编辑器，一次性完成
  syncCredentialParts();
  form.rateLimitPolicy =
      row.rateLimitPolicy && typeof row.rateLimitPolicy === "object" ? {...row.rateLimitPolicy} : {};
  form.quotaPolicy = row.quotaPolicy && typeof row.quotaPolicy === "object" ? {...row.quotaPolicy} : {};
  if (form.code) {
    codeValidated.value = true;
  }
}

async function resetForm() {
  codeError.value = "";
  codeValidated.value = false;
  if (props.provider) {
    const row = props.provider;
    fillFormFromRow(row);
    if (row.id) {
      detailLoading.value = true;
      try {
        const detail = await getProvider(row.id);
        if (detail) {
          fillFormFromRow(detail);
        }
      } finally {
        detailLoading.value = false;
      }
    }
  } else {
    form.id = null;
    form.providerId = null;
    form.baseUrl = "";
    form.enabled = true;
    form.credentialType = "api-key";
    form.authConfig = defaultAuthConfig();
    syncCredentialParts();
    form.rateLimitPolicy = {};
    form.quotaPolicy = {};
    loadSuggestedCode();
  }
}

async function loadProviderOptions() {
  try {
    const result = await listProviderProfiles({pageNum: 1, pageSize: 500});
    const list = Array.isArray(result) ? result : result?.list || [];
    providerOptions.value = list.map((item) => ({
      value: item.id,
      label: `${item.name || item.code}（${item.code}）`
    }));
  } catch {
    providerOptions.value = [];
  }
}

watch(
    () => [props.modelValue, props.provider],
    ([visible]) => {
      if (visible) {
        resetForm();
      }
    }
);

loadProviderOptions();

watch(
    () => form.code,
    () => {
      if (!props.modelValue) return;
      codeValidated.value = false;
      if (codeError.value) {
        codeError.value = "";
      }
    }
);

function onClosed() {
  form.id = null;
}

const CODE_MAX_LEN = 32;

async function validateCode() {
  const code = form.code?.trim();
  if (!code) {
    codeError.value = "请填写供应商编码";
    codeValidated.value = false;
    return false;
  }
  if (code.length > CODE_MAX_LEN) {
    codeError.value = `供应商编码不能超过 ${CODE_MAX_LEN} 个字符`;
    codeValidated.value = false;
    return false;
  }
  codeChecking.value = true;
  try {
    const res = await checkProviderCode(code, isEdit.value ? form.id : null);
    if (res?.available) {
      codeError.value = "";
      codeValidated.value = true;
      return true;
    }
    codeError.value = "供应商编码已存在，请更换";
    codeValidated.value = false;
    return false;
  } catch {
    codeValidated.value = false;
    return false;
  } finally {
    codeChecking.value = false;
  }
}

function buildPayload() {
  const authConfig = {...customAuthConfig.value};
  requiredCredentialFields.value.forEach((field) => {
    authConfig[field] = form.authConfig[field] ?? "";
  });
  Object.keys(authConfig).forEach((k) => {
    if (!k?.trim() || !String(authConfig[k] ?? "").trim()) delete authConfig[k];
  });
  return {
    providerId: form.providerId,
    code: form.code?.trim(),
    baseUrl: form.baseUrl?.trim() || null,
    enabled: form.enabled,
    credentialType: form.credentialType,
    authConfig: Object.keys(authConfig).length ? authConfig : null,
    rateLimitPolicy: Object.keys(form.rateLimitPolicy).length ? form.rateLimitPolicy : null,
    quotaPolicy: Object.keys(form.quotaPolicy).length ? form.quotaPolicy : null
  };
}

async function submit() {
  for (const field of requiredCredentialFields.value) {
    if (!String(form.authConfig[field] ?? "").trim()) {
      ElMessage.warning(`请填写 ${credentialFieldLabels[field] || field}`);
      return;
    }
  }
  if (!(await validateCode())) {
    return;
  }
  saving.value = true;
  try {
    const payload = buildPayload();
    if (isEdit.value) {
      await updateProvider(form.id, payload);
      ElMessage.success("已保存");
    } else {
      await createProvider(payload);
      ElMessage.success("已创建");
    }
    emit("update:modelValue", false);
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.auth-kv-editor {
  width: 100%;
}
</style>
