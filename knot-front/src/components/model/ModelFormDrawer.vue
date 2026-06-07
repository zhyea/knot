<template>
  <el-drawer
    :model-value="modelValue"
    :title="isEdit ? '编辑供应商模型' : '新建供应商模型'"
    size="62%"
    class="drawer-with-scrollbar"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >
    <el-scrollbar max-height="calc(100vh - 140px)">
    <el-form v-loading="detailLoading" :model="form" label-width="110px" class="model-form">
      <div class="slot-body model-section">
        <div class="section-head">
          <div>
            <h3>基础信息</h3>
            <p>维护供应商侧真实模型编码、名称、类型和版本。</p>
          </div>
          <el-form-item label="启用" class="inline-switch">
            <el-switch v-model="form.enabled" :before-change="beforeEnableChange" />
          </el-form-item>
        </div>

        <el-row :gutter="16" class="form-grid">
          <el-col :span="12">
            <el-form-item label="模型编码" required :error="modelCodeError">
              <el-input
                v-model="form.modelCode"
                placeholder="请输入供应商模型编码"
                maxlength="128"
                show-word-limit
                :disabled="modelCodeChecking"
                @blur="validateModelCode"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="form.name" placeholder="请输入模型名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16" class="form-grid">
          <el-col :span="12">
            <el-form-item label="模型类型" required>
              <EnumSelect v-model="form.modelType" category="model_type" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本">
              <el-input v-model="form.version" placeholder="如 2024-08-06 或 1.0.0" />
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <div class="space-line" />

      <div class="slot-body model-section">
        <div class="section-head">
          <div>
            <h3>绑定配置</h3>
            <p>维护统一模型、供应商和计费规则之间的关系；启用前必须配置完整。</p>
          </div>
        </div>

        <div class="binding-stack">
          <div class="binding-card">
            <div class="binding-card__head">
              <span>统一模型</span>
              <small>调用方感知的模型名称</small>
            </div>
            <el-form-item label="绑定统一模型" required class="bind-block-item">
              <RemoteEntitySelect
                v-model="form.logicalModelId"
                :load-function="loadLogicalModels"
                :label-function="logicalModelLabel"
                :selected-options="selectedLogicalModelOptions"
                placeholder="请选择统一模型"
                style="width: 100%"
              />
            </el-form-item>
            <el-table v-if="selectedLogicalModel" :data="[selectedLogicalModel]" border class="bind-table logical-model-bind-table">
              <el-table-column prop="modelCode" label="统一模型编码" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="bind-list__text">{{ row.modelCode || "—" }}</span>
                </template>
              </el-table-column>
              <el-table-column label="统一模型名称" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="bind-list__text">{{ logicalModelName(row) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="modelType" label="模型类型" width="100" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="bind-list__text">{{ row.modelType || "—" }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="modelFamily" label="模型族" width="120" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="bind-list__text">{{ row.modelFamily || "—" }}</span>
                </template>
              </el-table-column>
              <el-table-column label="是否启用" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.enabled === false ? 'info' : 'success'">
                    {{ row.enabled === false ? "停用" : "启用" }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="binding-grid">
            <div class="binding-card">
              <div class="binding-card__head">
                <span>供应商</span>
                <small>凭证和上游地址来源</small>
              </div>
              <el-form-item label="绑定供应商" required class="bind-block-item">
                <RemoteEntitySelect
                  v-model="form.providerId"
                  :load-function="loadProviders"
                  :label-function="providerLabel"
                  :selected-options="selectedProviderOptions"
                  placeholder="请选择供应商"
                  style="width: 100%"
                />
              </el-form-item>
              <el-table v-if="selectedProvider" :data="[selectedProvider]" border class="bind-table provider-bind-table">
                <el-table-column prop="code" label="供应商编码" min-width="160" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="bind-list__text">{{ row.code || "—" }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="name" label="供应商名称" min-width="160" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="bind-list__text">{{ row.name || "—" }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="是否启用" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.enabled === false ? 'info' : 'success'">
                      {{ row.enabled === false ? "停用" : "启用" }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <div class="binding-card">
              <div class="binding-card__head">
                <span>计费规则</span>
                <small>按供应商和统一模型筛选</small>
              </div>
              <el-form-item label="绑定计费规则" required class="bind-block-item">
            <RemoteEntitySelect
              v-model="form.billingRuleId"
              :load-function="loadBillingRules"
              :label-function="billingRuleLabel"
              :selected-options="selectedBillingRuleOptions"
              :extra-params="billingRuleFilterParams"
              :disabled="!form.providerId || !form.logicalModelId"
              placeholder="请选择计费规则"
              style="width: 100%"
            />
              </el-form-item>
              <el-table v-if="selectedBillingRule" :data="[selectedBillingRule]" border class="bind-table billing-rule-bind-table">
                <el-table-column prop="code" label="规则编码" min-width="150" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="bind-list__text">{{ row.code || "—" }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="name" label="规则名称" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="bind-list__text">{{ row.name || "—" }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="版本" width="80" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="bind-list__text">v{{ row.versionNo || 1 }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="是否启用" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.enabled === false ? 'info' : 'success'">
                      {{ row.enabled === false ? "停用" : "启用" }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </div>
      </div>

      <div class="space-line" />

      <div class="slot-body model-section">
        <div class="section-head">
          <div>
            <h3>调用配置</h3>
            <p>按接口协议维护上游路径，并配置响应中的 Usage 解析器。</p>
          </div>
          <el-button size="small" @click="addApiBinding">新增协议</el-button>
        </div>
        <div class="api-usage-section">
          <div v-if="form.apiBindings.length === 0" class="empty-api-binding">
            暂未配置 API 协议，网关会使用协议默认路径和厂商默认 usage 解析逻辑。
          </div>
          <div v-for="(binding, index) in form.apiBindings" :key="binding.uid" class="api-binding-card">
            <div class="api-binding-card__head">
              <span>协议 {{ index + 1 }}</span>
              <el-button text type="danger" @click="removeApiBinding(index)">删除</el-button>
            </div>
            <el-row :gutter="12" class="api-binding-card__row">
              <el-col :span="8">
                <el-form-item label="接口协议" required>
                  <EnumSelect
                    v-model="binding.protocol"
                    category="model_api_protocol"
                    :include-codes="allowedApiProtocolCodes"
                    show-code
                  />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="上游路径">
                  <el-input v-model="binding.apiPath" placeholder="为空时使用协议默认路径" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="启用">
                  <el-switch v-model="binding.enabled" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="12" class="api-binding-card__row">
              <el-col :span="12">
                <el-form-item label="Usage解析器">
                  <el-select
                    v-model="binding.usageExtractor"
                    filterable
                    placeholder="请选择 Usage 解析器"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="item in usageExtractorOptions"
                      :key="item.code"
                      :label="usageExtractorLabel(item)"
                      :value="item.code"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="流式解析器">
                  <el-select
                    v-model="binding.streamUsageExtractor"
                    clearable
                    filterable
                    placeholder="留空时复用 Usage解析器"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="item in streamUsageExtractorOptions"
                      :key="item.code"
                      :label="usageExtractorLabel(item)"
                      :value="item.code"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </div>
        </div>
      </div>

      <div class="space-line" />

      <TrafficPolicySection
        class="slot-body model-section"
        title="策略配置"
        description="按需配置模型级频控和额度策略；留空则不在模型维度覆盖。"
        rate-label="Rate Limit"
        v-model:rate-limit="form.rateLimitPolicy"
        v-model:quota="form.quotaPolicy"
        :columns="2"
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
import { computed, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import EnumSelect from "../common/EnumSelect.vue";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import TrafficPolicySection from "../common/TrafficPolicySection.vue";
import { useEnums } from "../../composables/useEnums";
import {
  emptyQuotaPolicy,
  emptyRateLimitPolicy,
  isEmptyQuotaPolicy,
  isEmptyRateLimitPolicy,
  normalizeQuotaPolicy,
  normalizeRateLimitPolicy
} from "../../utils/trafficPolicy";
import { checkModelCode, createModel, getModel, listUsageExtractors, updateModel } from "../../api/models";
import { listLogicalModels } from "../../api/logicalModels";
import { listProviders } from "../../api/providers";
import { listBillingRules } from "../../api/billing";
import { mergeOptionList, normalizeOptionList, resolveSelectedOption } from "../../utils/options";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  model: { type: Object, default: null }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const { options: modelTypeOptions, loadOptions: loadModelTypes } = useEnums("model_type");
const providerOptions = ref([]);
const logicalModelOptions = ref([]);
const billingRuleOptions = ref([]);
const usageExtractorOptions = ref([]);
const saving = ref(false);
const detailLoading = ref(false);
const modelCodeChecking = ref(false);
const modelCodeError = ref("");
const modelCodeValidated = ref(false);
const resettingForm = ref(false);

const MODEL_CODE_MAX_LEN = 128;
const FALLBACK_API_PROTOCOL_CODES = ["CUSTOM", "OTHER"];
const MODEL_TYPE_API_PROTOCOLS = {
  CHAT: [
    "CHAT_COMPLETIONS",
    "RESPONSES",
    "MESSAGES",
    "COMPLETIONS",
    "OPENAI_CHAT_COMPLETIONS",
    "OPENAI_RESPONSES",
    "ANTHROPIC_MESSAGES",
    "OPENAI_COMPLETIONS"
  ],
  TEXT: [
    "CHAT_COMPLETIONS",
    "RESPONSES",
    "MESSAGES",
    "COMPLETIONS",
    "OPENAI_CHAT_COMPLETIONS",
    "OPENAI_RESPONSES",
    "ANTHROPIC_MESSAGES",
    "OPENAI_COMPLETIONS"
  ],
  REASONING: [
    "CHAT_COMPLETIONS",
    "RESPONSES",
    "MESSAGES",
    "COMPLETIONS",
    "OPENAI_CHAT_COMPLETIONS",
    "OPENAI_RESPONSES",
    "ANTHROPIC_MESSAGES",
    "OPENAI_COMPLETIONS"
  ],
  MULTIMODAL: [
    "CHAT_COMPLETIONS",
    "RESPONSES",
    "MESSAGES",
    "COMPLETIONS",
    "OPENAI_CHAT_COMPLETIONS",
    "OPENAI_RESPONSES",
    "ANTHROPIC_MESSAGES",
    "OPENAI_COMPLETIONS",
    "IMAGE_GENERATIONS",
    "IMAGE_EDITS",
    "IMAGE_VARIATIONS",
    "AUDIO_TRANSCRIPTIONS",
    "AUDIO_TRANSLATIONS",
    "AUDIO_SPEECH",
    "VIDEO_GENERATIONS"
  ],
  EMBEDDING: ["EMBEDDINGS"],
  IMAGE: ["IMAGE_GENERATIONS", "IMAGE_EDITS", "IMAGE_VARIATIONS"],
  AUDIO: ["AUDIO_TRANSCRIPTIONS", "AUDIO_TRANSLATIONS", "AUDIO_SPEECH"],
  VIDEO: ["VIDEO_GENERATIONS"],
  RERANK: ["RERANK"],
  DOCUMENT: ["CHAT_COMPLETIONS", "RESPONSES", "MESSAGES"],
  OCR: ["CHAT_COMPLETIONS", "RESPONSES", "MESSAGES"],
  MODERATION: ["MODERATIONS"],
  UTILITY: ["RERANK", "MODERATIONS"]
};

const form = reactive({
  id: null,
  modelCode: "",
  name: "",
  providerId: null,
  logicalModelId: null,
  billingRuleId: null,
  modelType: "",
  version: "1.0.0",
  enabled: false,
  rateLimitPolicy: emptyRateLimitPolicy(),
  quotaPolicy: emptyQuotaPolicy(),
  apiBindings: []
});

const isEdit = computed(() => props.model?.id != null);
const selectedProvider = computed(() => providerOptions.value.find((item) => item.id === form.providerId));
const selectedLogicalModel = computed(() => logicalModelOptions.value.find((item) => item.id === form.logicalModelId));
const selectedBillingRule = computed(() => billingRuleOptions.value.find((item) => item.id === form.billingRuleId));
const selectedProviderOptions = computed(() =>
  resolveSelectedOption(form.providerId, providerOptions.value, {
    id: form.providerId,
    name: props.model?.providerName
  })
);
const selectedLogicalModelOptions = computed(() =>
  resolveSelectedOption(form.logicalModelId, logicalModelOptions.value, {
    id: form.logicalModelId
  })
);
const selectedBillingRuleOptions = computed(() =>
  resolveSelectedOption(form.billingRuleId, billingRuleOptions.value, {
    id: form.billingRuleId,
    name: props.model?.billingRuleName
  })
);
const billingRuleFilterParams = computed(() => ({
  providerId: form.providerId ?? undefined,
  logicalModelId: form.logicalModelId ?? undefined
}));
const streamUsageExtractorOptions = computed(() =>
  usageExtractorOptions.value.filter((item) => item.streamSupported !== false)
);
const allowedApiProtocolCodes = computed(() => allowedProtocolsForModelType(form.modelType));

async function loadProviders(params = { pageNum: 1, pageSize: 10 }) {
  const data = await listProviders(params);
  mergeOptions(providerOptions, normalizeOptionList(data));
  return data;
}

async function loadLogicalModels(params = { pageNum: 1, pageSize: 10 }) {
  const data = await listLogicalModels(params);
  const list = Array.isArray(data?.list) ? data.list.filter(isEnabledLogicalModel) : [];
  mergeOptions(logicalModelOptions, list);
  logicalModelOptions.value = logicalModelOptions.value.filter(isEnabledLogicalModel);
  return { ...(data || {}), list };
}

async function loadBillingRules(params = { pageNum: 1, pageSize: 10 }) {
  const data = await listBillingRules({
    ...params,
    providerId: params.providerId ?? form.providerId ?? undefined,
    logicalModelId: params.logicalModelId ?? form.logicalModelId ?? undefined
  });
  mergeOptions(billingRuleOptions, normalizeOptionList(data));
  return data;
}

async function loadUsageExtractors() {
  const data = await listUsageExtractors();
  usageExtractorOptions.value = Array.isArray(data) ? data : [];
  return data;
}

function mergeOptions(targetRef, list) {
  targetRef.value = mergeOptionList(targetRef.value, list);
}

function providerLabel(provider) {
  return provider.name || provider.code || `#${provider.id}`;
}

function logicalModelName(model) {
  return model.displayName || model.modelName || model.modelCode || `#${model.id}`;
}

function logicalModelLabel(model) {
  return model.modelCode ? `${logicalModelName(model)} (${model.modelCode})` : logicalModelName(model);
}

function isEnabledLogicalModel(model) {
  return model?.enabled !== false;
}

function billingRuleLabel(rule) {
  return rule.code ? `${rule.name || rule.code} (${rule.code}, v${rule.versionNo || 1})` : `#${rule.id}`;
}

function usageExtractorLabel(item) {
  return item?.label ? `${item.label} (${item.code})` : item?.code || "";
}

function fillForm(row) {
  form.id = row.id;
  form.modelCode = row.modelCode || "";
  form.name = row.name || "";
  form.providerId = row.providerId ?? null;
  form.logicalModelId = row.logicalModelId ?? null;
  form.billingRuleId = row.billingRuleId ?? null;
  form.modelType = row.modelType || "";
  form.version = row.version || "1.0.0";
  form.enabled = row.enabled === true;
  form.rateLimitPolicy = normalizeRateLimitPolicy(row.rateLimitPolicy);
  form.quotaPolicy = normalizeQuotaPolicy(row.quotaPolicy);
  form.apiBindings = normalizeApiBindings(row.apiBindings);
  if (form.modelCode) {
    modelCodeValidated.value = true;
  }
}

watch(
  () => form.modelCode,
  () => {
    if (!props.modelValue) return;
    modelCodeValidated.value = false;
    if (modelCodeError.value) {
      modelCodeError.value = "";
    }
  }
);

watch(
  () => [form.providerId, form.logicalModelId],
  ([providerId, logicalModelId], [oldProviderId, oldLogicalModelId]) => {
    if (!props.modelValue || resettingForm.value) {
      return;
    }
    if (providerId !== oldProviderId || logicalModelId !== oldLogicalModelId) {
      form.billingRuleId = null;
      billingRuleOptions.value = [];
    }
  }
);

watch(
  () => form.modelType,
  () => {
    if (!props.modelValue || resettingForm.value) {
      return;
    }
    normalizeApiBindingProtocols();
  }
);

async function resetForm() {
  resettingForm.value = true;
  modelCodeError.value = "";
  modelCodeValidated.value = false;
  try {
    if (props.model) {
      fillForm(props.model);
      if (isEdit.value) {
        detailLoading.value = true;
        try {
          const detail = await getModel(props.model.id);
          if (detail) {
            fillForm(detail);
          }
        } finally {
          detailLoading.value = false;
        }
      }
    } else {
      form.id = null;
      form.modelCode = "";
      form.name = "";
      form.providerId = providerOptions.value[0]?.id ?? null;
      form.logicalModelId = null;
      form.billingRuleId = null;
      form.modelType = modelTypeOptions.value[0]?.itemCode || "CHAT";
      form.version = "1.0.0";
      form.enabled = false;
      form.rateLimitPolicy = emptyRateLimitPolicy();
      form.quotaPolicy = emptyQuotaPolicy();
      form.apiBindings = [];
    }
  } finally {
    resettingForm.value = false;
  }
}

watch(
  () => [props.modelValue, props.model],
  async ([visible]) => {
    if (visible) {
      await Promise.all([loadProviders(), loadLogicalModels(), loadBillingRules(), loadModelTypes(), loadUsageExtractors()]);
      await resetForm();
    }
  }
);

function onClosed() {
  form.id = null;
  modelCodeError.value = "";
}

async function validateModelCode() {
  const code = form.modelCode?.trim();
  if (!code) {
    modelCodeError.value = "请填写模型编码";
    modelCodeValidated.value = false;
    return false;
  }
  if (code.length > MODEL_CODE_MAX_LEN) {
    modelCodeError.value = `模型编码不能超过 ${MODEL_CODE_MAX_LEN} 个字符`;
    modelCodeValidated.value = false;
    return false;
  }
  modelCodeChecking.value = true;
  try {
    const res = await checkModelCode(code, isEdit.value ? form.id : null);
    if (res?.available) {
      modelCodeError.value = "";
      modelCodeValidated.value = true;
      return true;
    }
    modelCodeError.value = `模型编码「${code}」已存在，请更换后重试`;
    modelCodeValidated.value = false;
    return false;
  } catch {
    modelCodeValidated.value = false;
    return false;
  } finally {
    modelCodeChecking.value = false;
  }
}

function validateRequired(showMessage = true) {
  const checks = [
    [form.modelCode?.trim(), "请填写模型编码"],
    [form.name?.trim(), "请填写名称"],
    [form.providerId, "请选择供应商"],
    [form.modelType?.trim(), "请选择模型类型"],
    [form.logicalModelId, "请选择统一模型"],
    [form.billingRuleId, "请选择计费规则"]
  ];
  const failed = checks.find(([ok]) => !ok);
  if (failed && showMessage) {
    ElMessage.warning(failed[1]);
  }
  if (failed) {
    return false;
  }
  if (!selectedLogicalModel.value || !isEnabledLogicalModel(selectedLogicalModel.value)) {
    if (showMessage) {
      ElMessage.warning("只能绑定已启用的统一模型");
    }
    return false;
  }
  return true;
}

function beforeEnableChange() {
  if (!form.enabled && !validateRequired(true)) {
    return false;
  }
  return true;
}

function createApiBinding(source = {}) {
  return {
    uid: source.uid || `${Date.now()}-${Math.random().toString(36).slice(2)}`,
    id: source.id ?? null,
    protocol: normalizeProtocolForModelType(source.protocol),
    apiPath: source.apiPath || "",
    usageExtractor: source.usageExtractor || "DEFAULT",
    streamUsageExtractor: source.streamUsageExtractor || "",
    enabled: source.enabled !== false,
    remark: source.remark || ""
  };
}

function normalizeApiBindings(list) {
  return Array.isArray(list) ? list.map((item) => createApiBinding(item)) : [];
}

function addApiBinding() {
  const usedProtocols = new Set(form.apiBindings.map((item) => normalizeProtocolCode(item.protocol)).filter(Boolean));
  form.apiBindings.push(createApiBinding({ protocol: firstAvailableProtocolCode(usedProtocols) }));
}

function removeApiBinding(index) {
  form.apiBindings.splice(index, 1);
}

function validateApiBindings() {
  const protocols = new Set();
  for (const binding of form.apiBindings) {
    if (!binding.protocol) {
      ElMessage.warning("Please select API protocol");
      return false;
    }
    if (!isProtocolAllowedForModelType(binding.protocol)) {
      ElMessage.warning(`API protocol does not match model type: ${binding.protocol}`);
      return false;
    }
    if (protocols.has(binding.protocol)) {
      ElMessage.warning("API protocol cannot be duplicated");
      return false;
    }
    protocols.add(binding.protocol);
    if (!binding.usageExtractor?.trim()) {
      ElMessage.warning(`Usage extractor is required: ${binding.protocol}`);
      return false;
    }
  }
  return true;
}

function allowedProtocolsForModelType(modelType) {
  const type = String(modelType || "CHAT").trim().toUpperCase();
  const codes = MODEL_TYPE_API_PROTOCOLS[type] || MODEL_TYPE_API_PROTOCOLS.CHAT;
  return [...codes, ...FALLBACK_API_PROTOCOL_CODES];
}

function isProtocolAllowedForModelType(protocol) {
  const code = normalizeProtocolCode(protocol);
  return Boolean(code) && allowedApiProtocolCodes.value.includes(code);
}

function firstAllowedProtocolCode() {
  return allowedApiProtocolCodes.value[0] || "CHAT_COMPLETIONS";
}

function firstAvailableProtocolCode(usedProtocols = new Set()) {
  return allowedApiProtocolCodes.value.find((code) => !usedProtocols.has(code)) || firstAllowedProtocolCode();
}

function normalizeProtocolCode(protocol) {
  return String(protocol || "").trim().toUpperCase();
}

function normalizeProtocolForModelType(protocol, usedProtocols = new Set()) {
  const code = normalizeProtocolCode(protocol);
  return code && isProtocolAllowedForModelType(code) && !usedProtocols.has(code)
    ? code
    : firstAvailableProtocolCode(usedProtocols);
}

function normalizeApiBindingProtocols() {
  const usedProtocols = new Set();
  for (const binding of form.apiBindings) {
    binding.protocol = normalizeProtocolForModelType(binding.protocol, usedProtocols);
    usedProtocols.add(binding.protocol);
  }
}

function buildApiBindingsPayload() {
  return form.apiBindings.map((binding) => ({
    id: binding.id,
    protocol: binding.protocol,
    apiPath: binding.apiPath?.trim() || null,
    usageExtractor: binding.usageExtractor?.trim() || "DEFAULT",
    streamUsageExtractor: binding.streamUsageExtractor?.trim() || null,
    enabled: binding.enabled,
    remark: binding.remark?.trim() || null
  }));
}

function buildPayload() {
  return {
    modelCode: form.modelCode?.trim(),
    name: form.name?.trim(),
    providerId: form.providerId,
    logicalModelId: form.logicalModelId,
    billingRuleId: form.billingRuleId,
    modelType: form.modelType,
    version: form.version,
    enabled: form.enabled,
    rateLimitPolicy: isEmptyRateLimitPolicy(form.rateLimitPolicy) ? null : normalizeRateLimitPolicy(form.rateLimitPolicy),
    quotaPolicy: isEmptyQuotaPolicy(form.quotaPolicy) ? null : normalizeQuotaPolicy(form.quotaPolicy),
    apiBindings: buildApiBindingsPayload()
  };
}

async function submit() {
  if (!validateRequired(true)) {
    return;
  }
  if (!(await validateModelCode())) {
    return;
  }
  if (!validateApiBindings()) {
    return;
  }
  saving.value = true;
  try {
    const payload = buildPayload();
    if (isEdit.value) {
      await updateModel(form.id, payload);
      ElMessage.success("已保存");
    } else {
      await createModel(payload);
      ElMessage.success("已创建");
    }
    emit("update:modelValue", false);
    emit("saved");
  } catch (err) {
    const msg = err?.message || "";
    if (msg.includes("模型编码")) {
      modelCodeError.value = msg;
    }
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.model-form {
  padding-bottom: 8px;
}

.model-section {
  border-color: #e4e7ed;
  box-shadow: 0 8px 24px rgba(31, 45, 61, 0.04);
}

.form-grid :deep(.el-form-item) {
  margin-bottom: 14px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.section-head h3 {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: var(--knot-text, #303133);
}

.section-head p {
  margin: 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.inline-switch {
  flex: 0 0 auto;
  margin-bottom: 0;
}

.space-line {
  height: 14px;
}

.binding-stack {
  display: grid;
  gap: 14px;
}

.binding-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: 1fr;
}

.binding-card,
.api-binding-card {
  border: 1px solid var(--knot-border, #e4e7ed);
  background: var(--knot-panel, #fff);
  padding: 14px;
}

.binding-card__head,
.api-binding-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--knot-border, #ebeef5);
}

.binding-card__head span,
.api-binding-card__head span {
  color: var(--knot-text, #303133);
  font-size: 13px;
  font-weight: 600;
}

.binding-card__head small {
  color: #909399;
  font-size: 12px;
}

.bind-block-item {
  margin-bottom: 10px;
}

.bind-table {
  margin-top: 10px;
  width: 100%;
}

.bind-table :deep(.el-table__cell) {
  font-size: 12px;
}

.bind-table :deep(th.el-table__cell) {
  font-size: 12px;
  font-weight: 600;
}

.bind-table :deep(.cell) {
  padding-left: 12px;
  padding-right: 12px;
}

.bind-list__text {
  overflow: hidden;
  color: #303133;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.api-usage-section {
  display: grid;
  gap: 12px;
}

.api-binding-card__row + .api-binding-card__row {
  margin-top: 4px;
}

.empty-api-binding {
  border: 1px dashed var(--knot-border, #dcdfe6);
  background: var(--knot-panel-muted, #fafafa);
  color: #909399;
  font-size: 12px;
  padding: 14px;
  text-align: center;
}

@media (max-width: 900px) {
  .section-head {
    display: block;
  }

  .inline-switch {
    margin-top: 12px;
  }

}
</style>
