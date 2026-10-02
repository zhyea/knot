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
              <el-form-item label="规则编码" required>
                <el-input
                  v-model="form.code"
                  placeholder="如 TOKEN_GPT4O"
                  maxlength="64"
                />
                <div v-if="codeBlockedByBinding" class="form-tip">
                  已被 {{ boundModelCount }} 个供应商模型使用，需先解绑才能修改
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="模型族">
                <EnumSelect
                  v-model="form.modelFamily"
                  category="model_family"
                  clearable
                  filterable
                  placeholder="不选则作为默认规则"
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
        <div class="space-line"/>

        <div class="slot-body form-section">
          <div class="section-head">
            <h3>计费策略</h3>
          </div>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="计费模式" class="billing-strategy-label">
                <EnumControl
                  v-model="form.billingMode"
                  enum-name="BillingModeEnum"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="进阶方案" class="billing-strategy-label">
                <EnumControl
                  v-model="form.pricingPlan"
                  enum-name="PricingPlanEnum"
                  :include-codes="planCodes"
                  show-code
                />
              </el-form-item>
            </el-col>
            <el-col v-if="form.billingMode !== 'FREE'" :span="12">
              <el-form-item label="计费单位" class="billing-strategy-label">
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
          <div class="billing-base-card">
            <div class="billing-base-card__title">基础计费规则</div>
            <component :is="modeComponent" :form="form"/>
          </div>
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
import type {Component} from "vue";
import {ElMessage} from "element-plus";
import type {Dict, Row} from "@/types";
import EnumSelect from "../common/EnumSelect.vue";
import EnumControl from "../common/EnumControl.vue";
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
import PricingPlanPeakOffPeakConfig from "./plans/PricingPlanPeakOffPeakConfig.vue";
import {createBillingRule, updateBillingRule, listModeCapabilities} from "@/api/billing";
import {isValidJsonText, parseJsonObject, stringifyJson} from "@/utils/format";
import {basePricesOf, createTierPriceSet, createTierRow, parseTierRows, toTierPayload, validateTierRows} from "@/utils/billingTier";
import type {TierRow} from "@/utils/billingTier";
import {createDefaultPeakPricing, parsePeakPricing, toPeakPayload, validatePeakPricing} from "@/utils/billingPeakOffPeak";
import type {PeakPricing} from "@/utils/billingPeakOffPeak";

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
  TIERED: PricingPlanTieredConfig,
  PEAK_OFF_PEAK: PricingPlanPeakOffPeakConfig
};

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value)
});

const saving = ref(false);
const billingModes = ref<Row[]>([]);

const activeModeCapability = computed(() =>
  billingModes.value.find((item) => item.code === form.billingMode) || null
);
const unitCodes = computed(() => activeModeCapability.value?.supportedUnits || []);
/** 当前模式支持的进阶方案；能力矩阵由后端下发，PEAK_OFF_PEAK 首期只对 TOKEN 开放 */
const planCodes = computed(() => activeModeCapability.value?.supportedPricingPlans || ["FIXED"]);

interface BillingRuleFormState {
  id: number | string | null;
  /** 规则业务码，新建时手工填写（大写归一由后端处理），编辑时不可改 */
  code: string;
  /** 模型族 code（ks_enum_configs.category=model_family 的 item_code）；空串表示默认规则，覆盖所有族 */
  modelFamily: string;
  billingMode: string;
  /** 进阶定价方案：FIXED / TIERED / PEAK_OFF_PEAK */
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
  cacheWrite5mUnitPrice: number;
  cacheWrite1hUnitPrice: number;
  cacheWriteMode: "standard" | "ttl";
  videoPrice720p: number | null;
  videoPrice1080p: number | null;
  imageResolution: string;
  imageQuality: string;
  /** 阶梯档位（pricingPlan=TIERED），保存时由 {@link toTierPayload} 并入 configJson.tier */
  tiers: TierRow[];
  /** 高低峰配置（pricingPlan=PEAK_OFF_PEAK），保存时由 {@link toPeakPayload} 并入 configJson.pricing */
  peakPricing: PeakPricing | null;
  customConfigJson: string;
  enabled: boolean;
  remark: string;
}

const form = reactive<BillingRuleFormState>({
  id: null,
  code: "",
  modelFamily: "",
  billingMode: "TOKEN",
  pricingPlan: "FIXED",
  currency: "USD",
  unit: "1M_TOKENS",
  unitPrice: 0.002,
  inputUnitPrice: 0.002,
  outputUnitPrice: 0.002,
  cacheReadUnitPrice: 0,
  cacheWriteUnitPrice: 0,
  cacheWrite5mUnitPrice: 0,
  cacheWrite1hUnitPrice: 0,
  cacheWriteMode: "standard",
  videoPrice720p: null,
  videoPrice1080p: null,
  imageResolution: "",
  imageQuality: "",
  tiers: [],
  peakPricing: null,
  customConfigJson: "",
  enabled: true,
  remark: ""
});

const isEdit = computed(() => props.rule != null);
/** 只读派生：绑定了该编码的供应商模型数（后端 RuleColumns 子查询带出） */
const boundModelCount = computed(() => Number(props.rule?.boundModelCount ?? 0));
/** 编辑且编码已被使用时不允许改码：绑定存的是 code，改了存量模型会掉绑 */
const codeBlockedByBinding = computed(() => isEdit.value && boundModelCount.value > 0);
/** 是否真的改了编码（后端大写归一，这里同口径比较） */
const codeChanged = computed(() =>
  String(props.rule?.code || "").trim().toUpperCase() !== resolveRuleCode().toUpperCase()
);
const modeComponent = computed(() => componentsByMode[form.billingMode] || BillingModeTokenConfig);
const planComponent = computed(() => componentsByPlan[form.pricingPlan] || PricingPlanFixedConfig);

watch(
  () => [props.modelValue, props.rule],
  async ([value]) => {
    if (!value) {
      return;
    }
    resetForm();
    await Promise.all([loadModeCapabilities()]);
  }
);

watch(
  () => form.billingMode,
  (mode) => applyModeDefaults(mode)
);

watch(
  () => form.cacheWriteMode,
  (mode) => {
    if (mode === "standard") {
      form.cacheWrite5mUnitPrice = 0;
      form.cacheWrite1hUnitPrice = 0;
    } else {
      form.cacheWriteUnitPrice = 0;
    }
  }
);

// 切换模式时若当前方案不被支持则回退固定价；方案切换保留基础价格，仅清理方案专属配置由后端校验兜底
watch(
  () => form.pricingPlan,
  (plan) => {
    if (plan && !planCodes.value.includes(plan)) {
      form.pricingPlan = "FIXED";
      return;
    }
    // 首次切到阶梯价时给一个起始档位，避免空白表格无从下手
    if (plan === "TIERED" && !form.tiers.length) {
      form.tiers.push(
        createTierRow({
          from: 0,
          unitPrices: basePricesOf(form, form.billingMode) || createTierPriceSet(null)
        })
      );
    }
    // 首次切到高低峰时补默认骨架（工作日高峰 + 兜底低峰）
    if (plan === "PEAK_OFF_PEAK" && !form.peakPricing) {
      form.peakPricing = createDefaultPeakPricing();
    }
  }
);

function resetForm() {
  const row = props.rule;
  const config = parseJsonObject(row?.configJson);
  const basePrices = (config.basePrices && typeof config.basePrices === "object") ? config.basePrices : {};
  form.id = row?.id ?? null;
  form.code = row?.code || "";
  form.modelFamily = row?.modelFamily ?? "";
  form.billingMode = normalizeMode(row?.billingMode || "TOKEN");
  form.pricingPlan = String(row?.pricingPlan || "FIXED").trim().toUpperCase();
  form.currency = row?.currency || "USD";
  form.unit = row?.unit || modeDefaults(form.billingMode).unit;
  form.unitPrice = Number(config.defaultUnitPrice ?? 0.002);
  form.inputUnitPrice = Number(basePrices.input ?? config.defaultUnitPrice ?? 0.002);
  form.outputUnitPrice = Number(basePrices.output ?? config.defaultUnitPrice ?? 0.002);
  form.cacheReadUnitPrice = Number(basePrices.cacheRead ?? 0);
  form.cacheWriteUnitPrice = Number(basePrices.cacheWrite ?? 0);
  form.cacheWrite5mUnitPrice = Number(basePrices.cacheWrite5m ?? 0);
  form.cacheWrite1hUnitPrice = Number(basePrices.cacheWrite1h ?? 0);
  form.cacheWriteMode = basePrices.cacheWrite5m != null || basePrices.cacheWrite1h != null ? "ttl" : "standard";
  form.videoPrice720p = config.resolutionPrices?.["720P"] != null ? Number(config.resolutionPrices["720P"]) : null;
  form.videoPrice1080p = config.resolutionPrices?.["1080P"] != null ? Number(config.resolutionPrices["1080P"]) : null;
  form.imageResolution = config.imageResolution || "";
  form.imageQuality = config.imageQuality || "";
  form.tiers = parseTierRows(config.tier);
  // 高低峰回显：存过的用原配置，没有（或结构缺失）则给一份默认骨架
  form.peakPricing = parsePeakPricing(config.pricing) || createDefaultPeakPricing();
  form.customConfigJson = form.billingMode === "CUSTOM" ? row?.configJson || "" : "";
  form.enabled = row?.enabled !== false;
  form.remark = row?.remark || "";
  applyModeDefaults(form.billingMode);
}

/** 当前模式的默认单位；能力未加载完成时退回新建表单的初值 */
function modeDefaults(mode: string): { unit: string } {
  const matched = billingModes.value.find((item) => item.code === mode);
  return {
    unit: matched?.defaultUnit || "1M_TOKENS"
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
        cacheWrite: form.cacheWriteMode === "standard" ? form.cacheWriteUnitPrice : 0,
        cacheWrite5m: form.cacheWriteMode === "ttl" ? form.cacheWrite5mUnitPrice : 0,
        cacheWrite1h: form.cacheWriteMode === "ttl" ? form.cacheWrite1hUnitPrice : 0
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
    // 档位缺失项以 0 落库，满足后端「每档 6 项单价齐全」约束
    return stringifyJson({
      ...(base || {}),
      tier: toTierPayload(form.tiers)
    });
  }
  if (form.pricingPlan === "PEAK_OFF_PEAK") {
    // 方案层只输出相位与倍率，价格本体来自上方基础价
    return stringifyJson({
      ...(base || {}),
      pricing: toPeakPayload(form.peakPricing)
    });
  }
  return base ? stringifyJson(base) : null;
}

/** 规则编码由人工填写（后端做大写归一与唯一性校验），编辑时只读 */
function resolveRuleCode(): string {
  return form.code.trim();
}

function buildPayload() {
  return {
    code: resolveRuleCode(),
    modelFamily: form.modelFamily ? form.modelFamily : null,
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
  if (!resolveRuleCode()) {
    ElMessage.warning("请填写规则编码");
    return;
  }
  // 保存前确认编码未被使用：被绑定的编码改了会让存量模型掉绑（后端亦有同名硬校验）
  if (codeBlockedByBinding.value && codeChanged.value) {
    ElMessage.warning(`规则编码已被 ${boundModelCount.value} 个供应商模型使用，请先解绑后再修改`);
    return;
  }
  const tierIssues = form.pricingPlan === "TIERED" ? validateTierRows(form.tiers) : [];
  if (tierIssues.length) {
    ElMessage.warning(tierIssues[0].message);
    return;
  }
  const peakIssues = form.pricingPlan === "PEAK_OFF_PEAK" ? validatePeakPricing(form.peakPricing) : [];
  if (peakIssues.length) {
    ElMessage.warning(peakIssues[0].message);
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

.billing-base-card {
  margin-top: 12px;
  padding: 12px 14px 4px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}

.billing-base-card__title {
  margin-bottom: 12px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

:deep(.billing-usage-field .el-form-item__label) {
  background: #e4f5ed;
  padding: 0 8px;
}

:deep(.billing-strategy-label .el-form-item__label) {
  background: #f7dce8;
  padding: 0 8px;
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

.form-tip {
  width: 100%;
  color: var(--el-color-warning);
  font-size: 12px;
  line-height: 1.5;
}
</style>
