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
              <p>维护供应商模型编码、统一模型、上游模型、版本以及上游基础地址；名称与类型由绑定的统一模型派生。</p>
            </div>
            <el-form-item label="启用" class="inline-switch">
              <el-switch v-model="form.enabled" :before-change="beforeEnableChange"/>
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
                  @blur="onModelCodeBlur"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="版本">
                <el-input v-model="form.version" placeholder="如 v2026092910 或 1.0.0" @focus="fillVersionOnFocus">
                  <template #append>
                    <el-button :icon="RefreshRight" title="生成最新版本" @click="refreshVersion"/>
                  </template>
                </el-input>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16" class="form-grid">
            <el-col :span="24">
              <el-form-item label="供应商账户" required>
                <ProviderAccountSelect
                  v-model="form.providerAccountCode"
                  placeholder="请选择供应商账户"
                  style="width: 100%"
                  @change="onProviderAccountChange"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16" class="form-grid">
            <el-col :span="24">
              <el-form-item label="Base URL" required>
                <el-input v-model="form.baseUrl" placeholder="https://api.example.com"/>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16" class="form-grid">
            <el-col :span="12">
              <el-form-item label="统一模型" required>
                <RemoteEntitySelect
                  v-model="form.logicalModelCode"
                  value-key="value"
                  :load-function="loadLogicalModelOptions"
                  :code-only="false"
                  code-key="code"
                  label-key="label"
                  placeholder="请选择统一模型"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="上游模型" required>
                <el-input
                  v-model="form.upstreamModel"
                  placeholder="向上游请求时使用的 model，如 gpt-4o"
                  maxlength="128"
                  show-word-limit
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="备注">
            <el-input
              v-model="form.remark"
              type="textarea"
              :rows="2"
              maxlength="255"
              show-word-limit
              placeholder="选填，记录模型用途、注意事项等"
            />
          </el-form-item>
        </div>

        <div class="space-line"/>

        <div class="slot-body model-section">
          <div class="section-head">
            <div>
              <h3>绑定配置</h3>
              <p>维护计费规则绑定；启用前必须配置完整。</p>
            </div>
          </div>

          <div class="binding-card">
            <div class="binding-card__head">
              <span>计费规则</span>
              <small>下拉选择规则（按统一模型的模型族筛选候选）；下方只回显当前已绑定的那一条</small>
            </div>
            <el-form-item label="绑定计费规则" required class="bind-block-item">
              <RemoteEntitySelect
                v-model="form.billingRuleCode"
                value-key="value"
                :load-function="loadBillingRuleOptions"
                :code-only="false"
                code-key="code"
                label-key="label"
                :extra-params="billingRuleFilterParams"
                :disabled="!form.providerAccountCode || !form.logicalModelCode"
                placeholder="请选择计费规则"
                style="width: 100%"
              />
            </el-form-item>
            <el-table
              v-if="boundBillingRule"
              :data="[boundBillingRule]"
              row-key="code"
              border
              class="bind-table"
            >
              <el-table-column prop="code" label="规则编码" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="bind-list__text">{{ row.code || "-" }}</span>
                </template>
              </el-table-column>
              <el-table-column label="模型族" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="bind-list__text">{{
                      row.modelFamilyName || row.modelFamilyCode || "默认（所有模型族）"
                    }}</span>
                </template>
              </el-table-column>
              <el-table-column label="计费模式" min-width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ modeLabel(row.billingMode) }}</template>
              </el-table-column>
              <el-table-column label="进阶方案" min-width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ planLabel(row.pricingPlan) }}</template>
              </el-table-column>
              <el-table-column label="是否启用" width="90" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.enabled === false ? 'info' : 'success'">
                    {{ row.enabled === false ? "停用" : "启用" }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
            <div v-else class="empty-billing-rule">
              {{
                form.billingRuleCode ? "已绑定的计费规则不存在或已删除，请重新选择" : "尚未绑定计费规则，请从上方下拉选择"
              }}
            </div>
          </div>
        </div>

        <div class="space-line"/>

        <div class="slot-body model-section">
          <div class="section-head">
            <div>
              <h3>调用配置</h3>
              <p>按接口协议维护上游路径，并配置请求适配器与 Usage 解析器。</p>
            </div>
            <el-button size="small" @click="addApiBinding">新增协议</el-button>
          </div>
          <div class="api-usage-section">
            <div v-if="form.apiBindings.length === 0" class="empty-api-binding">
              暂未配置 API 协议，网关会使用协议默认路径和供应商默认 Usage 解析逻辑。
            </div>
            <div v-for="(binding, index) in form.apiBindings" :key="binding.uid" class="api-binding-card">
              <div class="api-binding-card__head">
                <span>协议 {{ index + 1 }}</span>
                <el-button text type="danger" @click="removeApiBinding(index)">删除</el-button>
              </div>

              <el-row :gutter="12" class="api-binding-card__row">
                <el-col :span="8">
                  <el-form-item label="接口协议" required>
                    <EnumControl
                      v-model="binding.protocol"
                      enum-name="ModelApiProtocolEnum"
                      filterable
                      placeholder="请选择接口协议"
                      :include-codes="allowedApiProtocolCodes"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="10">
                  <el-form-item label="请求适配器">
                    <el-select
                      v-model="binding.requestAdapter"
                      default-first-option
                      clearable
                      filterable
                      placeholder="留空时使用 OpenAI Compatible"
                      style="width: 100%"
                    >
                      <el-option
                        v-for="item in requestAdapterOptions"
                        :key="item.code"
                        :label="requestAdapterLabel(item)"
                        :value="item.code"
                      />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="6">
                  <el-form-item label="启用">
                    <el-switch v-model="binding.enabled"/>
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="12" class="api-binding-card__row">
                <el-col :span="24">
                  <el-form-item label="上游路径">
                    <el-input v-model="binding.apiPath" placeholder="为空时使用协议默认路径"/>
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="12" class="api-binding-card__row">
                <el-col :span="12">
                  <el-form-item label="Usage">
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
                  <el-form-item label="流式Usage">
                    <el-select
                      v-model="binding.streamUsageExtractor"
                      clearable
                      filterable
                      placeholder="留空时复用 Usage 解析器"
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

        <div class="space-line"/>

        <TrafficPolicySection
          class="slot-body model-section"
          mode="rate"
          title="限流配置"
          description="按需配置模型级限流；留空则不在模型维度覆盖。"
          v-model:rate-limit="form.rateLimitPolicy"
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
import {EnabledStatus} from "@/constants/status";
import {type PropType, computed, reactive, ref, watch} from "vue";
import type {Ref} from "vue";
import {ElMessage} from "element-plus";
import {RefreshRight} from "@element-plus/icons-vue";
import type {Dict, Row, OptionItem, ProviderAccountMeta} from "@/types";
import EnumControl from "../common/EnumControl.vue";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import TrafficPolicySection from "../common/TrafficPolicySection.vue";
import ProviderAccountSelect from "../provider/ProviderAccountSelect.vue";
import {useModelTypes} from "@/composables/useModelTypes";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {
  emptyRateLimitPolicy,
  isEmptyRateLimitPolicy,
  normalizeRateLimitPolicy,
  type RateLimitPolicy
} from "@/utils/trafficPolicy";
import {
  checkModelCode,
  createModel,
  getModel,
  listRequestAdapters,
  listUsageExtractors,
  updateModel
} from "@/api/models";
import {listBillingRules} from "@/api/billing";
import {listBillingRuleOptions, listLogicalModelOptions} from "@/api/options";
import {normalizeOptionList, resolveSelectedOption, toOptionsLoader} from "@/utils/options";
import {useMissingOptionGuard} from "@/composables/useMissingOptionGuard";

/** 模型 API 绑定行（表单内 uid 用于 :key 稳定渲染） */
interface ModelApiBinding {
  uid: string;
  id: number | null;
  protocol: string;
  apiPath: string;
  requestAdapter: string;
  usageExtractor: string;
  streamUsageExtractor: string;
  enabled: boolean;
  remark: string;
}

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  model: {type: Object as PropType<Dict | null>, default: null},
  /** 统一模型下拉仅展示 modelCode */
  codeOnly: {type: Boolean, default: false}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const {loadOptions: loadModelTypes, protocolsOf} = useModelTypes();
const {labelOf: enumLabelOf} = useEnumOptions();
const logicalModelOptions = ref<Row[]>([]);
/** 当前已绑定的计费规则行（按 code 精确取回，表格只展示它）；未绑定为 null */
const boundBillingRule = ref<Row | null>(null);
const usageExtractorOptions = ref<Row[]>([]);
const requestAdapterOptions = ref<Row[]>([]);
const saving = ref(false);
const detailLoading = ref(false);
const modelCodeChecking = ref(false);
const modelCodeError = ref("");
const modelCodeValidated = ref(false);
const resettingForm = ref(false);

const MODEL_CODE_MAX_LEN = 128;

interface ModelFormState {
  id: number | string | null;
  modelCode: string;
  /** 上游模型：向上游发起请求时写入请求体 model 参数，与 modelCode 可不同 */
  upstreamModel: string;
  baseUrl: string;
  remark: string;
  providerAccountCode: string | null;
  logicalModelCode: string | null;
  /** 绑定计费规则用业务码 code（kb_billing_rules.code），不绑主键 id */
  billingRuleCode: string | null;
  version: string;
  enabled: boolean;
  rateLimitPolicy: RateLimitPolicy;
  apiBindings: ModelApiBinding[];
}

const form = reactive<ModelFormState>({
  id: null,
  modelCode: "",
  upstreamModel: "",
  baseUrl: "",
  remark: "",
  providerAccountCode: null,
  logicalModelCode: null,
  billingRuleCode: null,
  version: "",
  enabled: false,
  rateLimitPolicy: emptyRateLimitPolicy(),
  apiBindings: []
});

const isEdit = computed(() => props.model?.id != null);
const selectedLogicalModel = computed(() => logicalModelOptions.value.find((item) => item.value === form.logicalModelCode));
const metaOf = (row: Row | undefined): Row => (row?.meta as Row) ?? {};
/** 计费规则按「模型族」过滤：模型族由绑定的统一模型派生（后端 br.model_family 存 item_code） */
const billingRuleFilterParams = computed(() => ({
  modelFamilyCode: metaOf(selectedLogicalModel.value).modelFamily || undefined
}));
const streamUsageExtractorOptions = computed(() =>
  usageExtractorOptions.value.filter((item) => item.streamSupported !== false)
);
const allowedApiProtocolCodes = computed(() =>
  allowedProtocolsForModelType(String(metaOf(selectedLogicalModel.value).modelType || ""))
);

const loadLogicalModelOptions = toOptionsLoader(listLogicalModelOptions, logicalModelOptions);

const loadBillingRuleOptions = toOptionsLoader(listBillingRuleOptions);

/**
 * 按业务码精确取回已绑定的那一条规则（表格只展示它）。
 *
 * <p>走 code 精确匹配而非 keyword 模糊搜索 —— 模糊搜可能命中不到、或命中一堆同前缀规则。
 */
async function loadBoundBillingRule(code: string | null) {
  const target = code?.trim();
  if (!target) {
    boundBillingRule.value = null;
    return;
  }
  const data = await listBillingRules({pageNum: 1, pageSize: 1, code: target});
  boundBillingRule.value = normalizeOptionList(data).find((item) => item.code === target) ?? null;
}

async function loadUsageExtractors() {
  const data = await listUsageExtractors();
  usageExtractorOptions.value = Array.isArray(data) ? data : [];
  return data;
}

async function loadRequestAdapters() {
  const data = await listRequestAdapters();
  requestAdapterOptions.value = Array.isArray(data) ? data : [];
  return data;
}

function isEnabledLogicalModel(model: Row): boolean {
  if (!model) {
    return false;
  }
  const status = metaOf(model).status;
  // 状态已数字化：1-启用 0-停用；0 是合法值，禁止 truthy 判断
  return status == null ? model.disabled !== true : status === EnabledStatus.ENABLED;
}

function modeLabel(code: unknown): string {
  return enumLabelOf("BillingModeEnum", code, String(code || "-"));
}

function planLabel(code: unknown): string {
  return enumLabelOf("PricingPlanEnum", code, String(code || "-"));
}

function usageExtractorLabel(item: Row): string {
  return item?.label || item?.code || "";
}

function requestAdapterLabel(item: Row): string {
  return item?.label || item?.code || "";
}

/** 选中供应商账户后回填上游 Base URL。后端把 baseUrl 放在 options 候选项的强类型 meta 里（非敏感地址）。 */
function onProviderAccountChange(account: OptionItem<ProviderAccountMeta> | null) {
  form.baseUrl = account?.meta?.baseUrl ?? "";
}

/** 新建时的默认版本：打开表单时的当前时间（小时级，如 v2026092910） */
function defaultVersion(): string {
  return `v${formatHourVersion(new Date())}`;
}

/** 版本输入框获得焦点时，若为空则自动以当前时间（小时级，如 v2026092910）填充；不覆盖已有值 */
function fillVersionOnFocus() {
  if (!String(form.version ?? "").trim()) {
    form.version = defaultVersion();
  }
}

/** 刷新按钮：无论是否为空，直接以当前时间（小时级）重新生成最新版本 */
function refreshVersion() {
  form.version = defaultVersion();
}

function formatHourVersion(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}${pad(date.getHours())}`;
}

function fillForm(row: Row) {
  form.id = row.id;
  form.modelCode = row.modelCode || "";
  form.upstreamModel = row.upstreamModel || "";
  form.baseUrl = row.baseUrl || "";
  form.remark = row.remark || "";
  form.providerAccountCode = row.providerAccountCode ?? null;
  form.logicalModelCode = row.logicalModelCode ?? null;
  form.billingRuleCode = row.billingRuleCode ?? null;
  form.version = row.version || "1.0.0";
  form.enabled = row.enabled === true;
  form.rateLimitPolicy = normalizeRateLimitPolicy(row.rateLimitPolicy);
  form.apiBindings = normalizeApiBindings(row.apiBindings);
  if (form.modelCode) {
    modelCodeValidated.value = true;
  }
}

watch(
  () => form.modelCode,
  () => {
    if (!props.modelValue) {
      return;
    }
    modelCodeValidated.value = false;
    if (modelCodeError.value) {
      modelCodeError.value = "";
    }
  }
);

watch(
  () => [form.providerAccountCode, form.logicalModelCode],
  async ([providerAccountCode, logicalModelCode], [oldAccountCode, oldLogicalModelCode]) => {
    if (!props.modelValue || resettingForm.value) {
      return;
    }
    if (providerAccountCode !== oldAccountCode || logicalModelCode !== oldLogicalModelCode) {
      // 模型族随统一模型变化，已绑定的规则不再适用，一并清空
      form.billingRuleCode = null;
      boundBillingRule.value = null;
    }
  }
);

// 表格只回显已绑定那一条：绑定码变化即按 code 精确取回（含下拉选择后的即时回显）
watch(
  () => form.billingRuleCode,
  async (code) => {
    if (!props.modelValue) {
      return;
    }
    await loadBoundBillingRule(code);
  }
);

watch(
  () => selectedLogicalModel.value?.modelType,
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
      form.upstreamModel = "";
      form.baseUrl = "";
      form.remark = "";
      form.providerAccountCode = null;
      form.logicalModelCode = null;
      form.billingRuleCode = null;
      form.version = defaultVersion();
      form.enabled = false;
      form.rateLimitPolicy = emptyRateLimitPolicy();
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
      await resetForm();
      await Promise.all([
        // 带 values 精确回显已绑定统一模型，保证 selectedLogicalModel（协议联动/提交校验）在首屏即有值
        loadLogicalModelOptions({values: form.logicalModelCode ? [form.logicalModelCode] : []}),
        loadModelTypes(),
        loadUsageExtractors(),
        loadRequestAdapters()
      ]);
      // 表单重置后按已绑定码取回那一条规则（watch 在同值时不触发，这里显式补一次）
      await loadBoundBillingRule(form.billingRuleCode);
    }
  }
);

function onClosed() {
  form.id = null;
  modelCodeError.value = "";
}

/** 模型编码失焦：上游模型为空时以模型编码兜底（多数供应商上游模型与编码同名），再走唯一性校验 */
function onModelCodeBlur() {
  if (!String(form.upstreamModel ?? "").trim()) {
    form.upstreamModel = form.modelCode?.trim() || "";
  }
  void validateModelCode();
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
    modelCodeError.value = `模型编码“${code}”已存在，请更换后重试`;
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
  const checks: Array<[unknown, string]> = [
    [form.modelCode?.trim(), "请填写模型编码"],
    [form.upstreamModel?.trim(), "请填写上游模型"],
    [form.baseUrl?.trim(), "请填写 Base URL"],
    [form.providerAccountCode, "请选择供应商账户"],
    [form.logicalModelCode, "请选择统一模型"],
    [form.billingRuleCode, "请选择计费规则"]
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

function createApiBinding(source: Dict = {}): ModelApiBinding {
  return {
    uid: source.uid || `${Date.now()}-${Math.random().toString(36).slice(2)}`,
    id: source.id ?? null,
    protocol: normalizeProtocolForModelType(source.protocol),
    apiPath: source.apiPath || "",
    requestAdapter: source.requestAdapter || "",
    usageExtractor: source.usageExtractor || "DEFAULT",
    streamUsageExtractor: source.streamUsageExtractor || "",
    enabled: source.enabled !== false,
    remark: source.remark || ""
  };
}

function normalizeApiBindings(list: unknown): ModelApiBinding[] {
  return Array.isArray(list) ? list.map((item) => createApiBinding(item)) : [];
}

function addApiBinding() {
  const usedProtocols = new Set(form.apiBindings.map((item) => normalizeProtocolCode(item.protocol)).filter(Boolean));
  form.apiBindings.push(createApiBinding({protocol: firstAvailableProtocolCode(usedProtocols)}));
}

function removeApiBinding(index: number) {
  form.apiBindings.splice(index, 1);
}

function validateApiBindings() {
  const protocols = new Set();
  for (const binding of form.apiBindings) {
    if (!binding.protocol) {
      ElMessage.warning("请选择接口协议");
      return false;
    }
    if (!isProtocolAllowedForModelType(binding.protocol)) {
      ElMessage.warning(`接口协议与模型类型不匹配：${binding.protocol}`);
      return false;
    }
    if (protocols.has(binding.protocol)) {
      ElMessage.warning("接口协议不能重复");
      return false;
    }
    protocols.add(binding.protocol);
    if (!binding.usageExtractor?.trim()) {
      ElMessage.warning(`Usage 解析器必填：${binding.protocol}`);
      return false;
    }
  }
  return true;
}

function allowedProtocolsForModelType(modelType: string): string[] {
  // 可选协议完全由后端 /api/models/types 提供，前端不再维护类型到协议的映射
  return protocolsOf(modelType);
}

function isProtocolAllowedForModelType(protocol: string) {
  const code = normalizeProtocolCode(protocol);
  return Boolean(code) && allowedApiProtocolCodes.value.includes(code);
}

function firstAllowedProtocolCode() {
  return allowedApiProtocolCodes.value[0] || "CHAT_COMPLETIONS";
}

function firstAvailableProtocolCode(usedProtocols = new Set()) {
  return allowedApiProtocolCodes.value.find((code) => !usedProtocols.has(code)) || firstAllowedProtocolCode();
}

function normalizeProtocolCode(protocol: string) {
  return String(protocol || "").trim().toUpperCase();
}

function normalizeProtocolForModelType(protocol: string, usedProtocols = new Set<unknown>()) {
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
    requestAdapter: binding.requestAdapter?.trim() || null,
    usageExtractor: binding.usageExtractor?.trim() || "DEFAULT",
    streamUsageExtractor: binding.streamUsageExtractor?.trim() || null,
    enabled: binding.enabled,
    remark: binding.remark?.trim() || null
  }));
}

function buildPayload() {
  return {
    modelCode: form.modelCode?.trim(),
    upstreamModel: form.upstreamModel?.trim(),
    baseUrl: form.baseUrl?.trim() || null,
    remark: form.remark?.trim() || null,
    providerAccountCode: form.providerAccountCode,
    logicalModelCode: form.logicalModelCode,
    billingRuleCode: form.billingRuleCode,
    version: form.version,
    enabled: form.enabled,
    rateLimitPolicy: isEmptyRateLimitPolicy(form.rateLimitPolicy) ? null : normalizeRateLimitPolicy(form.rateLimitPolicy),
    apiBindings: buildApiBindingsPayload()
  };
}

// 下拉缺失值守卫：已选项被删除/无权限时阻止提交（options 契约第 10 条）
const missing = useMissingOptionGuard();

async function submit() {
  if (missing.hasMissing.value) {
    ElMessage.warning(missing.hint.value);
    return;
  }
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
      await updateModel(form.id!, payload);
      ElMessage.success("已保存");
    } else {
      await createModel(payload);
      ElMessage.success("已创建");
    }
    emit("update:modelValue", false);
    emit("saved");
  } catch (err) {
    const msg = (err as Error)?.message || "";
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

.empty-billing-rule {
  margin-top: 10px;
  border: 1px dashed var(--knot-border, #dcdfe6);
  background: var(--knot-panel-muted, #fafafa);
  color: #909399;
  font-size: 12px;
  padding: 14px;
  text-align: center;
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
