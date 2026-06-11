<template>
  <el-drawer
      :model-value="modelValue"
      :title="isEdit ? '编辑供应商' : '新建供应商'"
      size="50%"
      class="drawer-with-scrollbar"
      destroy-on-close
      @update:model-value="emit('update:modelValue', $event)"
      @closed="onClosed"
  >

    <el-scrollbar max-height="calc(100vh - 140px)">
    <el-form :model="form" label-width="100px">
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
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="供应商名称"/>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="类型">
              <EnumSelect v-model="form.type" category="provider_type" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用">
              <el-switch v-model="form.enabled"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="认证凭证">
          <KvEditor
              v-model="form.authConfig"
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
import {useAuth} from "../../composables/useAuth";
import EnumSelect from "../common/EnumSelect.vue";
import {
  createProvider,
  updateProvider,
  getProvider,
  suggestProviderCode,
  checkProviderCode
} from "../../api/providers";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  provider: {type: Object, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const {isAdmin} = useAuth();
const saving = ref(false);
const codeChecking = ref(false);
const codeError = ref("");
const codeValidated = ref(false);
const detailLoading = ref(false);
const form = reactive({
  id: null,
  code: "",
  name: "",
  type: "",
  enabled: true,
  authConfig: {apiKey: ""},
  rateLimitPolicy: {},
  quotaPolicy: {}
});

const isEdit = computed(() => props.provider != null);

function defaultAuthConfig() {
  return {apiKey: ""};
}

function normalizeAuthConfig(raw) {
  if (raw && typeof raw === "object" && Object.keys(raw).length > 0) {
    return {...raw};
  }
  return defaultAuthConfig();
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
  form.code = row.code || "";
  form.name = row.name;
  form.type = row.type;
  form.enabled = !!row.enabled;
  form.authConfig = normalizeAuthConfig(row.authConfig);
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
    form.name = "";
    form.type = "";
    form.enabled = true;
    form.authConfig = defaultAuthConfig();
    form.rateLimitPolicy = {};
    form.quotaPolicy = {};
    loadSuggestedCode();
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
  const authConfig = {...form.authConfig};
  Object.keys(authConfig).forEach((k) => {
    if (!k?.trim()) delete authConfig[k];
  });
  return {
    code: form.code?.trim(),
    name: form.name,
    type: form.type,
    enabled: form.enabled,
    authConfig: Object.keys(authConfig).length ? authConfig : null,
    rateLimitPolicy: Object.keys(form.rateLimitPolicy).length ? form.rateLimitPolicy : null,
    quotaPolicy: Object.keys(form.quotaPolicy).length ? form.quotaPolicy : null
  };
}

async function submit() {
  if (!form.name?.trim()) {
    ElMessage.warning("请填写名称");
    return;
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
