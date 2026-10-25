<template>
  <el-drawer
    :model-value="modelValue"
    :title="isEdit ? '编辑供应商账户' : '新建供应商账户'"
    size="900px"
    class="drawer-with-scrollbar"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >

    <el-scrollbar max-height="calc(100vh - 140px)">
      <el-form v-loading="detailLoading" :model="form" label-width="120px">
        <div class="slot-body">
          <div class="section-head">
            <div>
              <h3>基础信息</h3>
              <p>维护供应商账户编码、归属供应商、上游 Base URL 与认证凭证配置。</p>
            </div>
            <el-form-item label="启用" class="inline-switch">
              <el-switch v-model="form.enabled"/>
            </el-form-item>
          </div>
          <el-row :gutter="16">
            <el-col style="flex: 0 0 60%; max-width: 60%">
              <el-form-item label="编码" required :error="codeError">
                <el-input
                  v-model="form.code"
                  placeholder="最长 48 位"
                  maxlength="48"
                  show-word-limit
                  :disabled="codeChecking || codeLocked"
                  @blur="validateCode"
                />
                <div v-if="codeLocked" class="form-tip">{{ codeLockHint }}</div>
              </el-form-item>
            </el-col>
            <el-col style="flex: 0 0 40%; max-width: 40%">
              <el-form-item label="供应商" required>
                <RemoteEntitySelect
                  v-model="form.providerCode"
                  :load-function="loadProviderProfileOptions"
                  placeholder="请选择供应商"
                  clearable
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="Base URL">
            <el-input v-model="form.baseUrl" placeholder="https://api.example.com"/>
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="认证类型" required>
                <el-select
                  v-model="form.credentialType"
                  placeholder="请选择认证类型"
                  style="width: 100%"
                  @change="handleCredentialTypeChange"
                >
                  <el-option
                    v-for="option in credentialTypeOptions"
                    :key="option.code"
                    :label="option.label"
                    :value="option.code"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item v-if="form.credentialType" label="鉴权方式" required>
                <el-select
                  v-model="form.authApplier"
                  placeholder="请选择鉴权方式"
                  style="width: 100%"
                >
                  <el-option
                    v-for="option in availableAuthAppliers"
                    :key="option.code"
                    :label="option.label"
                    :value="option.code"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item
            v-for="field in credentialFieldDefs"
            :key="field.name"
            :label="credentialFieldLabel(field)"
            :required="isCredentialFieldRequired(field)"
          >
            <el-select
              v-if="credentialInputType(field) === 'select'"
              v-model="form.authConfig[field.name]"
              :placeholder="field.placeholder || `请选择 ${credentialFieldLabel(field)}`"
              filterable
              allow-create
              default-first-option
              style="width: 100%"
            >
              <el-option
                v-for="option in field.options || []"
                :key="option.value"
                :label="option.label || option.value"
                :value="option.value"
              />
            </el-select>
            <el-input
              v-else
              v-model="form.authConfig[field.name]"
              :type="credentialInputType(field)"
              :rows="credentialInputType(field) === 'textarea' ? 5 : undefined"
              :show-password="credentialInputType(field) === 'password' && isAdmin"
              autocomplete="off"
              :placeholder="field.placeholder || `请输入 ${credentialFieldLabel(field)}`"
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

        <div class="space-line"/>

        <TrafficPolicySection
          class="slot-body"
          mode="quota"
          title="限额配置"
          description="按统计窗口约束该供应商账户可消耗的 Token 总量与成本上限，可叠加多个不同窗口。"
          v-model:quotas="form.quotaPolicies"
        />
      </el-form>
    </el-scrollbar>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>

  </el-drawer>
</template>

<script setup lang="ts">
import {type PropType, computed, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import KvEditor from "../common/KvEditor.vue";
import TrafficPolicySection from "../common/TrafficPolicySection.vue";
import {
  emptyQuotaPolicies,
  isEmptyQuotaPolicy,
  normalizeQuotaPolicy,
  normalizeQuotaPolicies,
  type QuotaPolicy
} from "@/utils/trafficPolicy";
import {useAuth} from "@/composables/useAuth";
import {useCodeBindingLock} from "@/composables/useCodeBindingLock";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import {listProviderProfileOptions} from "@/api/options";
import {toOptionsLoader} from "@/utils/options";
import {
  createProvider,
  updateProvider,
  getProvider,
  checkProviderCode,
  listCredentialTypes,
  listAuthAppliers
} from "@/api/providers";
import type {Dict, Row} from "@/types";
import {useMissingOptionGuard} from "@/composables/useMissingOptionGuard";
import {
  buildCredentialValues,
  credentialFieldLabel,
  credentialInputType,
  isCredentialFieldRequired,
  normalizeCredentialFields,
  type CredentialFieldDef
} from "@/utils/credentialFields";

/** 认证类型选项：code / label / requiredFields（字段定义数组），由后端 ProviderCredentialTypeEnum 下发 */
interface CredentialTypeOption {
  code: string;
  label: string;
  requiredFields?: unknown;
}

/** 鉴权策略选项：code / label，由后端 UpstreamAuthApplierCatalog 下发 */
interface AuthApplierOption {
  code: string;
  label: string;
  /** 该策略适用的认证类型 code（用于按已选认证类型过滤） */
  credentialTypes?: string[];
}

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  // 只接收被编辑账户的 id：列表接口不再返回认证配置与策略，整表数据一律按 id 拉详情获取
  providerId: {type: Number as PropType<number | null>, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const {isAdmin} = useAuth();
const saving = ref(false);
const codeChecking = ref(false);
/** 编码引用处数（账户下挂载的供应商模型数）：后端在详情里下发，>0 时编码不可改 */
const boundCount = ref(0);
const {codeLocked, codeLockHint} = useCodeBindingLock(() => isEdit.value, () => boundCount.value);
const codeError = ref("");
const codeValidated = ref(false);
const detailLoading = ref(false);
const form = reactive<Dict>({
  id: null,
  providerCode: null,
  code: "",
  baseUrl: "",
  enabled: true,
  credentialType: null,
  authApplier: null,
  authConfig: {},
  quotaPolicies: emptyQuotaPolicies()
});

const isEdit = computed(() => props.providerId != null);

// 默认鉴权策略（选中认证类型后的默认勾选）；可选项由后端 /api/provider-accounts/auth-appliers 下发
const DEFAULT_AUTH_APPLIER = "BEARER";

const credentialTypeOptions = ref<CredentialTypeOption[]>([]);
const authApplierOptions = ref<AuthApplierOption[]>([]);

// 按已选认证类型过滤可选鉴权方式（后端随选项下发 credentialTypes 关联关系）
const availableAuthAppliers = computed<AuthApplierOption[]>(() => {
  const type = form.credentialType;
  if (!type) {
    return [];
  }
  return authApplierOptions.value.filter((option) => (option.credentialTypes || []).includes(type));
});

// 选定认证类型后的默认鉴权方式：优先默认策略，兼容不到则取第一个可选项
function defaultAuthApplierForType(): string | null {
  const available = availableAuthAppliers.value;
  if (available.some((option) => option.code === DEFAULT_AUTH_APPLIER)) {
    return DEFAULT_AUTH_APPLIER;
  }
  return available[0]?.code || null;
}

// 当前认证类型的字段定义（后端下发，前端不再自带字段名/控件类型映射表）
const credentialFieldDefs = computed<CredentialFieldDef[]>(() => {
  const matched = credentialTypeOptions.value.find((option) => option.code === form.credentialType);
  return normalizeCredentialFields(matched?.requiredFields);
});

const customAuthConfig = ref<Dict>({});

function defaultAuthConfig(): Dict {
  return {};
}

function normalizeAuthConfig(raw: unknown): Dict {
  if (raw && typeof raw === "object" && Object.keys(raw).length > 0) {
    return {...(raw as Dict)};
  }
  return defaultAuthConfig();
}

async function loadCredentialTypes() {
  const data = await listCredentialTypes();
  credentialTypeOptions.value = Array.isArray(data) ? data : [];
}

async function loadAuthAppliers() {
  try {
    const data = await listAuthAppliers();
    authApplierOptions.value = Array.isArray(data) ? data : [];
  } catch {
    authApplierOptions.value = [];
  }
}

function syncCredentialParts() {
  const defNames = new Set(credentialFieldDefs.value.map((field) => field.name));
  const custom: Dict = {};
  Object.entries(form.authConfig || {}).forEach(([key, value]) => {
    if (!defNames.has(key)) {
      custom[key] = value;
    }
  });
  customAuthConfig.value = custom;
}

function handleCredentialTypeChange() {
  // 切换认证类型后按新类型重置鉴权方式（取该类型下的默认可选项）
  form.authApplier = defaultAuthApplierForType();
  const defs = credentialFieldDefs.value;
  const defNames = new Set(defs.map((field) => field.name));
  // 新类型的字段：保留已填值（重叠字段如 accessKey/secretKey 不丢），空值用后端给的 default 预填
  const config: Dict = buildCredentialValues(defs, form.authConfig, customAuthConfig.value);
  // 仅保留用户在自定义编辑器里显式添加的键，旧类型残留字段一律丢弃
  Object.entries(customAuthConfig.value).forEach(([key, value]) => {
    if (!defNames.has(key)) {
      config[key] = value;
    }
  });
  form.authConfig = config;
}

function fillFormFromRow(row: Row) {
  form.id = row.id;
  form.providerCode = row.providerCode;
  form.code = row.code || "";
  form.baseUrl = row.baseUrl || "";
  form.enabled = !!row.enabled;
  form.credentialType = row.credentialType || null;
  form.authApplier = row.authApplier || defaultAuthApplierForType();
  form.authConfig = normalizeAuthConfig(row.authConfig);
  boundCount.value = Number(row.boundCount ?? 0);
  // 初次加载：把存量 authConfig 里不属于当前类型的键拆到自定义编辑器，一次性完成
  syncCredentialParts();
  form.quotaPolicies = normalizeQuotaPolicies(row.quotaPolicies);
  if (form.code) {
    codeValidated.value = true;
  }
}

function clearForm() {
  form.id = null;
  form.providerId = null;
  form.code = "";
  form.baseUrl = "";
  form.enabled = true;
  form.credentialType = null;
  form.authApplier = null;
  form.authConfig = defaultAuthConfig();
  boundCount.value = 0;
  syncCredentialParts();
  form.quotaPolicies = emptyQuotaPolicies();
}

async function resetForm() {
  codeError.value = "";
  codeValidated.value = false;
  clearForm();
  if (!props.providerId) {
    // 新建不预填编码，由用户手工填写（submit 前有唯一性校验兜底）
    return;
  }
  detailLoading.value = true;
  try {
    const detail = await getProvider(props.providerId);
    if (detail) {
      fillFormFromRow(detail);
    } else {
      ElMessage.error("未找到该供应商账户");
      emit("update:modelValue", false);
    }
  } catch {
    // 详情拿不到就关掉抽屉：留着空表单可能被误保存，覆盖掉真实配置
    ElMessage.error("加载供应商账户详情失败，请重试");
    emit("update:modelValue", false);
  } finally {
    detailLoading.value = false;
  }
}

/** 供应商信息下拉：value=code，label 由后端给出（name / code）；编辑态回显由组件自动带 values。 */
const loadProviderProfileOptions = toOptionsLoader(listProviderProfileOptions);

watch(
  () => [props.modelValue, props.providerId],
  ([visible]) => {
    if (visible) {
      resetForm();
    }
  }
);

loadCredentialTypes();
loadAuthAppliers();

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

const CODE_MAX_LEN = 48;

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

function buildPayload(): Dict {
  const authConfig: Dict = {...customAuthConfig.value};
  credentialFieldDefs.value.forEach((field) => {
    authConfig[field.name] = form.authConfig[field.name] ?? "";
  });
  Object.keys(authConfig).forEach((k) => {
    if (!k?.trim() || !String(authConfig[k] ?? "").trim()) delete authConfig[k];
  });
  return {
    providerCode: form.providerCode,
    code: form.code?.trim(),
    baseUrl: form.baseUrl?.trim() || null,
    enabled: form.enabled,
    credentialType: form.credentialType,
    authApplier: form.authApplier,
    authConfig: Object.keys(authConfig).length ? authConfig : null,
    quotaPolicies: (form.quotaPolicies as QuotaPolicy[])
      .map(normalizeQuotaPolicy)
      .filter((q: QuotaPolicy) => !isEmptyQuotaPolicy(q))
  };
}

// 下拉缺失值守卫：已选项被删除/无权限时阻止提交（options 契约第 10 条）
const missing = useMissingOptionGuard();

async function submit() {
  if (missing.hasMissing.value) {
    ElMessage.warning(missing.hint.value);
    return;
  }
  if (!form.credentialType) {
    ElMessage.warning("请选择认证类型");
    return;
  }
  if (!form.authApplier) {
    ElMessage.warning("请选择鉴权方式");
    return;
  }
  for (const field of credentialFieldDefs.value) {
    if (isCredentialFieldRequired(field) && !String(form.authConfig[field.name] ?? "").trim()) {
      ElMessage.warning(`请填写 ${credentialFieldLabel(field)}`);
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
.section-head p {
  margin: 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.auth-kv-editor {
  width: 100%;
}

</style>
