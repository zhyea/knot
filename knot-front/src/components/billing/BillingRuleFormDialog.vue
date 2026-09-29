<template>
  <el-drawer
    v-model="visible"
    :title="isEdit ? '编辑计费规则' : '新建计费规则'"
    direction="rtl"
    size="55%"
    class="drawer-with-scrollbar"
    destroy-on-close
  >
    <el-scrollbar max-height="calc(100vh - 140px)">
      <el-form :model="form" label-width="118px" class="billing-rule-form">
        <div class="slot-body form-section">
          <div class="section-head">
            <h3>基础信息</h3>
            <el-form-item label="启用" class="inline-switch">
              <el-switch v-model="form.enabled"/>
            </el-form-item>
          </div>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="供应商">
                <RemoteEntitySelect
                  v-model="form.providerCode"
                  value-key="code"
                  :load-function="loadProviders"
                  :label-function="providerLabel"
                  :selected-options="selectedProviderOptions"
                  clearable
                  placeholder="不选则作为全局规则"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="统一模型">
                <RemoteEntitySelect
                  v-model="form.logicalModelCode"
                  value-key="modelCode"
                  :load-function="loadLogicalModels"
                  :label-function="logicalModelLabel"
                  :selected-options="selectedLogicalModelOptions"
                  clearable
                  placeholder="不选则作为供应商默认规则"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="币种">
                <EnumSelect v-model="form.currency" category="billing_currency"/>
              </el-form-item>
            </el-col>
          </el-row>
        </div>
        <!-- 规则编码不再展示：新建时按「供应商类型-统一模型ID」自动生成，编辑沿用原编码 -->

        <div class="space-line"/>

        <div class="slot-body form-section">
          <div class="section-head">
            <h3>计费策略</h3>
          </div>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="计费模式">
                <EnumControl
                  v-model="form.billingMode"
                  enum-name="BillingModeEnum"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="进阶方案">
                <EnumControl
                  v-model="form.pricingPlan"
                  enum-name="PricingPlanEnum"
                  :include-codes="planCodes"
                  show-code
                />
              </el-form-item>
            </el-col>
            <el-col v-if="form.billingMode !== 'FREE'" :span="12">
              <el-form-item label="计费单位">
                <EnumSelect
                  v-model="form.unit"
                  category="billing_unit"
                  :include-codes="unitCodes"
                  show-code
                />
              </el-form-item>
            </el-col>
          </el-row>
          <!-- 第一层：模式组件（用量与基础价格）；第二层：方案组件（进阶定价明细） -->
          <component :is="modeComponent" :form="form"/>
          <component :is="planComponent" :form="form"/>
        </div>

        <div class="space-line"/>

        <div class="slot-body form-section">
          <div class="section-head">
            <h3>备注</h3>
          </div>
          <el-form-item label-width="0">
            <el-input v-model="form.remark" type="textarea" :rows="4" maxlength="500" show-word-limit/>
          </el-form-item>
        </div>
      </el-form>
    </el-scrollbar>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import {type PropType, computed, reactive, ref, watch} from "vue";
import type {Component, Ref} from "vue";
import {ElMessage} from "element-plus";
import type {Dict, Row} from "@/types";
import EnumSelect from "../common/EnumSelect.vue";
import EnumControl from "../common/EnumControl.vue";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import BillingModeAudioConfig from "./modes/BillingModeAudioConfig.vue";
import BillingModeCustomConfig from "./modes/BillingModeCustomConfig.vue";
import BillingModeEmbeddingConfig from "./modes/BillingModeEmbeddingConfig.vue";
import BillingModeFreeConfig from "./modes/BillingModeFreeConfig.vue";
import BillingModeImageConfig from "./modes/BillingModeImageConfig.vue";
import BillingModeRequestConfig from "./modes/BillingModeRequestConfig.vue";
import BillingModeTokenConfig from "./modes/BillingModeTokenConfig.vue";
import BillingModeVideoConfig from "./modes/BillingModeVideoConfig.vue";
import PricingPlanFixedConfig from "./plans/PricingPlanFixedConfig.vue";
import PricingPlanTieredConfig from "./plans/PricingPlanTieredConfig.vue";
import {createBillingRule, updateBillingRule, listModeCapabilities} from "@/api/billing";
import {listProviderProfiles} from "@/api/providerProfiles";
import {listLogicalModels} from "@/api/logicalModels";
import {isValidJsonText, parseJsonObject, stringifyJson} from "@/utils/format";
import {mergeOptionList, normalizeOptionList, resolveSelectedOption} from "@/utils/options";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  rule: {type: Object as PropType<Dict | null>, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

// 两层组件：模式组件决定“量”（基础价格），方案组件决定“价”（进阶定价明细）。
// 模式能力与方案支持矩阵由后端 GET /api/billing/mode-capabilities 下发，前端不维护映射表。
const componentsByMode: Record<string, Component> = {
  TOKEN: BillingModeTokenConfig,
  REQUEST: BillingModeRequestConfig,
  IMAGE: BillingModeImageConfig,
  AUDIO: BillingModeAudioConfig,
  VIDEO: BillingModeVideoConfig,
  EMBEDDING: BillingModeEmbeddingConfig,
  FREE: BillingModeFreeConfig,
  CUSTOM: BillingModeCustomConfig
};

const componentsByPlan: Record<string, Component> = {
  FIXED: PricingPlanFixedConfig,
  TIERED: PricingPlanTieredConfig
};

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value)
});

const saving = ref(false);
const providerOptions = ref<Row[]>([]);
const logicalModelOptions = ref<Row[]>([]);
const billingModes = ref<Row[]>([]);

const activeModeCapability = computed(() =>
  billingModes.value.find((item) => item.code === form.billingMode) || null
);
const unitCodes = computed(() => activeModeCapability.value?.supportedUnits || []);
/** 当前模式支持的进阶方案（PEAK_OFF_PEAK 属阶段三，后端不下发） */
const planCodes = computed(() => activeModeCapability.value?.supportedPricingPlans || ["FIXED"]);

interface BillingRuleFormState {
  id: number | string | null;
  /** 编辑时沿用原编码；新建时按「供应商类型-统一模型ID」自动生成 */
  code: string;
  providerCode: string | null;
  logicalModelCode: string | null;
  billingMode: string;
  /** 进阶定价方案（FIXED/TIERED），高低峰属阶段三 */
  pricingPlan: string;
  currency: string;
  unit: string;
  /** 简单模式（REQUEST/IMAGE/AUDIO/VIDEO/EMBEDDING）单价，写入 configJson.defaultUnitPrice */
  unitPrice: number;
  /** TOKEN 模式分项单价，写入 configJson.basePrices */
  inputUnitPrice: number;
  outputUnitPrice: number;
  cacheReadUnitPrice: number;
  cacheWriteUnitPrice: number;
  videoPrice720p: number | null;
  videoPrice1080p: number | null;
  imageResolution: string;
  imageQuality: string;
  /** 阶梯配置（pricingPlan=TIERED），保存时合并进 configJson.tier */
  tierJson: string;
  customConfigJson: string;
  enabled: boolean;
  remark: string;
}

const form = reactive<BillingRuleFormState>({
  id: null,
  code: "",
  providerCode: null,
  logicalModelCode: null,
  billingMode: "TOKEN",
  pricingPlan: "FIXED",
  currency: "USD",
  unit: "1K_TOKENS",
  unitPrice: 0.002,
  inputUnitPrice: 0.002,
  outputUnitPrice: 0.002,
  cacheReadUnitPrice: 0,
  cacheWriteUnitPrice: 0,
  videoPrice720p: null,
  videoPrice1080p: null,
  imageResolution: "",
  imageQuality: "",
  tierJson: "",
  customConfigJson: "",
  enabled: true,
  remark: ""
});

const isEdit = computed(() => props.rule != null);
const modeComponent = computed(() => componentsByMode[form.billingMode] || BillingModeTokenConfig);
const planComponent = computed(() => componentsByPlan[form.pricingPlan] || PricingPlanFixedConfig);
const selectedProviderOptions = computed(() =>
  resolveSelectedOption(form.providerCode, providerOptions.value, {
    id: form.providerCode,
    name: props.rule?.providerName
  })
);
const selectedLogicalModelOptions = computed(() =>
  resolveSelectedOption(form.logicalModelCode, logicalModelOptions.value, {
    id: form.logicalModelCode,
    modelName: props.rule?.logicalModelName
  })
);

watch(
  () => [props.modelValue, props.rule],
  async ([value]) => {
    if (!value) {
      return;
    }
    resetForm();
    await Promise.all([loadProviders(), loadLogicalModels(), loadModeCapabilities()]);
  }
);

watch(
  () => form.billingMode,
  (mode) => applyModeDefaults(mode)
);

// 切换模式时若当前方案不被支持则回退固定价；方案切换保留基础价格，仅清理方案专属配置由后端校验兜底
watch(
  () => form.pricingPlan,
  (plan) => {
    if (plan && !planCodes.value.includes(plan)) {
      form.pricingPlan = "FIXED";
    }
  }
);

function resetForm() {
  const row = props.rule;
  const config = parseJsonObject(row?.configJson);
  const basePrices = (config.basePrices && typeof config.basePrices === "object") ? config.basePrices : {};
  form.id = row?.id ?? null;
  form.code = row?.code || "";
  form.providerCode = row?.providerCode ?? null;
  form.logicalModelCode = row?.logicalModelCode ?? null;
  form.billingMode = normalizeMode(row?.billingMode || "TOKEN");
  form.pricingPlan = String(row?.pricingPlan || "FIXED").trim().toUpperCase();
  form.currency = row?.currency || "USD";
  form.unit = row?.unit || modeDefaults(form.billingMode).unit;
  form.unitPrice = Number(config.defaultUnitPrice ?? 0.002);
  form.inputUnitPrice = Number(basePrices.input ?? config.defaultUnitPrice ?? 0.002);
  form.outputUnitPrice = Number(basePrices.output ?? config.defaultUnitPrice ?? 0.002);
  form.cacheReadUnitPrice = Number(basePrices.cacheRead ?? 0);
  form.cacheWriteUnitPrice = Number(basePrices.cacheWrite ?? 0);
  form.videoPrice720p = config.resolutionPrices?.["720P"] != null ? Number(config.resolutionPrices["720P"]) : null;
  form.videoPrice1080p = config.resolutionPrices?.["1080P"] != null ? Number(config.resolutionPrices["1080P"]) : null;
  form.imageResolution = config.imageResolution || "";
  form.imageQuality = config.imageQuality || "";
  form.tierJson = Array.isArray(config.tier) ? stringifyJson(config.tier) : "";
  form.customConfigJson = form.billingMode === "CUSTOM" ? row?.configJson || "" : "";
  form.enabled = row?.enabled !== false;
  form.remark = row?.remark || "";
  applyModeDefaults(form.billingMode);
}

/** 当前模式的默认单位；能力未加载完成时退回新建表单的初值 */
function modeDefaults(mode: string): { unit: string } {
  const matched = billingModes.value.find((item) => item.code === mode);
  return {
    unit: matched?.defaultUnit || "1K_TOKENS"
  };
}

function applyModeDefaults(mode: string) {
  const defaults = modeDefaults(mode);
  if (!unitCodes.value.includes(form.unit)) {
    form.unit = defaults.unit;
  }
  // 模式切换后方案能力矩阵变化：不支持的方案回退固定价
  if (!planCodes.value.includes(form.pricingPlan)) {
    form.pricingPlan = "FIXED";
  }
}

async function loadModeCapabilities() {
  const data = await listModeCapabilities();
  billingModes.value = Array.isArray(data?.billingModes) ? data.billingModes : [];
}

async function loadProviders(params = {pageNum: 1, pageSize: 10}) {
  const res = await listProviderProfiles(params);
  mergeOptions(providerOptions, normalizeOptionList(res));
  return res;
}

async function loadLogicalModels(params = {pageNum: 1, pageSize: 10}) {
  const res = await listLogicalModels(params);
  mergeOptions(logicalModelOptions, normalizeOptionList(res));
  return res;
}

function mergeOptions(targetRef: Ref<Row[]>, list: Row[]) {
  targetRef.value = mergeOptionList(targetRef.value, list);
}

function providerLabel(provider: Row): string {
  return provider.name || provider.code || `#${provider.id}`;
}

function logicalModelLabel(model: Row): string {
  const name = model.displayName || model.modelName || model.modelCode || `#${model.id}`;
  return model.modelCode ? `${name} (${model.modelCode})` : name;
}

function validateJson(value: unknown, label: string): boolean {
  if (!isValidJsonText(value)) {
    ElMessage.warning(`${label}不是合法 JSON`);
    return false;
  }
  return true;
}

function normalizeMode(mode: unknown): string {
  // 取值由后端枚举 BillingModeEnum 下拉约束，这里只做格式归一与空值兜底
  return String(mode || "").trim().toUpperCase() || "TOKEN";
}

/** 基础价格部分（模式组件产出）；进阶方案明细在此基础上合并 */
function buildBaseConfig(): Dict | null {
  if (form.billingMode === "TOKEN") {
    return {
      basePrices: {
        input: form.inputUnitPrice,
        output: form.outputUnitPrice,
        cacheRead: form.cacheReadUnitPrice,
        cacheWrite: form.cacheWriteUnitPrice
      }
    };
  }
  if (form.billingMode === "IMAGE") {
    return {
      defaultUnitPrice: form.unitPrice,
      imageResolution: form.imageResolution?.trim() || null,
      imageQuality: form.imageQuality?.trim() || null
    };
  }
  if (form.billingMode === "VIDEO") {
    const resolutionPrices: Dict = {};
    if (form.videoPrice720p != null) {
      resolutionPrices["720P"] = form.videoPrice720p;
    }
    if (form.videoPrice1080p != null) {
      resolutionPrices["1080P"] = form.videoPrice1080p;
    }
    return {
      defaultUnitPrice: form.unitPrice,
      ...(Object.keys(resolutionPrices).length ? {resolutionPrices} : {})
    };
  }
  if (form.billingMode === "EMBEDDING") {
    return {defaultUnitPrice: form.inputUnitPrice};
  }
  if (form.billingMode === "REQUEST" || form.billingMode === "AUDIO") {
    return {defaultUnitPrice: form.unitPrice};
  }
  return null;
}

function buildConfigJson() {
  if (form.billingMode === "CUSTOM") {
    return form.customConfigJson?.trim() || null;
  }
  const base = buildBaseConfig();
  if (form.pricingPlan === "TIERED") {
    const tier = form.tierJson?.trim() ? parseJsonObject(form.tierJson) : null;
    return stringifyJson({
      ...(base || {}),
      ...(tier ? {tier} : {})
    });
  }
  return base ? stringifyJson(base) : null;
}

/**
 * 规则编码 = 供应商类型(code)-统一模型ID；未选统一模型视为供应商默认规则（-default），
 * 未选供应商则为全局规则（GLOBAL-DEFAULT）。编辑时沿用原编码，不重新生成。
 */
function resolveRuleCode(): string {
  if (isEdit.value) {
    return form.code.trim();
  }
  const provider = providerOptions.value.find((item) => item.code === form.providerCode);
  const providerType = String(provider?.code || provider?.name || "GLOBAL").trim().toUpperCase() || "GLOBAL";
  const modelPart = form.logicalModelCode == null ? "DEFAULT" : String(form.logicalModelCode);
  return `${providerType}-${modelPart}`;
}

function buildPayload() {
  return {
    code: resolveRuleCode(),
    providerCode: form.providerCode,
    logicalModelCode: form.logicalModelCode,
    billingMode: form.billingMode,
    pricingPlan: form.pricingPlan,
    currency: form.currency,
    unit: form.billingMode === "FREE" ? "1K_TOKENS" : form.unit,
    configJson: buildConfigJson(),
    enabled: form.enabled,
    remark: form.remark?.trim() || null
  };
}

async function submit() {
  if (form.pricingPlan === "TIERED" && !validateJson(form.tierJson, "阶梯配置")) {
    return;
  }
  if (form.billingMode === "CUSTOM" && !validateJson(form.customConfigJson, "自定义配置")) {
    return;
  }
  saving.value = true;
  try {
    const payload = buildPayload();
    if (isEdit.value) {
      await updateBillingRule(form.id!, payload);
    } else {
      await createBillingRule(payload);
    }
    ElMessage.success("已保存");
    visible.value = false;
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.billing-rule-form {
  padding-bottom: 8px;
}

.form-section {
  border-color: #e4e7ed;
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
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--knot-text, #303133);
}

.inline-switch {
  margin-bottom: 0;
}

.space-line {
  height: 14px;
}
</style>
