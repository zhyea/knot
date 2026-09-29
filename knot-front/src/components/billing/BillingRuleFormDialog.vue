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
                <EnumSelect
                  v-model="form.billingMode"
                  category="billing_mode"
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
          <component :is="modeComponent" :form="form"/>
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
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import BillingModeAudioConfig from "./modes/BillingModeAudioConfig.vue";
import BillingModeCustomConfig from "./modes/BillingModeCustomConfig.vue";
import BillingModeEmbeddingConfig from "./modes/BillingModeEmbeddingConfig.vue";
import BillingModeFreeConfig from "./modes/BillingModeFreeConfig.vue";
import BillingModeImageConfig from "./modes/BillingModeImageConfig.vue";
import BillingModeRequestConfig from "./modes/BillingModeRequestConfig.vue";
import BillingModeTieredConfig from "./modes/BillingModeTieredConfig.vue";
import BillingModeTokenConfig from "./modes/BillingModeTokenConfig.vue";
import BillingModeVideoConfig from "./modes/BillingModeVideoConfig.vue";
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

// 「模式 -> 可用单位 / 默认单位 / 默认价格项」由后端 BillingModeEnum 经
// GET /api/billing/mode-capabilities 下发；单位与价格项文案走 DB 枚举，前端不再维护映射表
const componentsByMode: Record<string, Component> = {
  TOKEN: BillingModeTokenConfig,
  REQUEST: BillingModeRequestConfig,
  IMAGE: BillingModeImageConfig,
  AUDIO: BillingModeAudioConfig,
  VIDEO: BillingModeVideoConfig,
  EMBEDDING: BillingModeEmbeddingConfig,
  TIERED: BillingModeTieredConfig,
  FREE: BillingModeFreeConfig,
  CUSTOM: BillingModeCustomConfig
};

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value)
});

const saving = ref(false);
const providerOptions = ref<Row[]>([]);
const logicalModelOptions = ref<Row[]>([]);
const modeCapabilities = ref<Row[]>([]);

const activeModeCapability = computed(() =>
  modeCapabilities.value.find((item) => item.code === form.billingMode) || null
);
const unitCodes = computed(() => activeModeCapability.value?.supportedUnits || []);

interface BillingRuleFormState {
  id: number | string | null;
  /** 编辑时沿用原编码；新建时按「供应商类型-统一模型ID」自动生成 */
  code: string;
  providerCode: string | null;
  logicalModelCode: string | null;
  billingMode: string;
  currency: string;
  unit: string;
  /** 简单模式（REQUEST/IMAGE/AUDIO/VIDEO/EMBEDDING/TIERED）单价，写入 configJson.defaultUnitPrice */
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
  /** 阶梯配置（TIERED），保存时合并进 configJson.ladder */
  ladderJson: string;
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
  ladderJson: "",
  customConfigJson: "",
  enabled: true,
  remark: ""
});

const isEdit = computed(() => props.rule != null);
const modeComponent = computed(() => componentsByMode[form.billingMode] || BillingModeTokenConfig);
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

function resetForm() {
  const row = props.rule;
  const config = parseJsonObject(row?.configJson);
  const basePrices = (config.basePrices && typeof config.basePrices === "object") ? config.basePrices : {};
  form.id = row?.id ?? null;
  form.code = row?.code || "";
  form.providerCode = row?.providerCode ?? null;
  form.logicalModelCode = row?.logicalModelCode ?? null;
  form.billingMode = normalizeMode(row?.billingMode || "TOKEN");
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
  form.ladderJson = Array.isArray(config.ladder) ? stringifyJson(config.ladder) : "";
  form.customConfigJson = form.billingMode === "CUSTOM" ? row?.configJson || "" : "";
  form.enabled = row?.enabled !== false;
  form.remark = row?.remark || "";
  applyModeDefaults(form.billingMode);
}

/** 当前模式的默认单位；能力未加载完成时退回新建表单的初值 */
function modeDefaults(mode: string): { unit: string } {
  const matched = modeCapabilities.value.find((item) => item.code === mode);
  return {
    unit: matched?.defaultUnit || "1K_TOKENS"
  };
}

function applyModeDefaults(mode: string) {
  const defaults = modeDefaults(mode);
  if (!unitCodes.value.includes(form.unit)) {
    form.unit = defaults.unit;
  }
}

async function loadModeCapabilities() {
  const data = await listModeCapabilities();
  modeCapabilities.value = Array.isArray(data) ? data : [];
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
  // 取值由 DB 枚举 billing_mode 下拉约束，这里只做格式归一与空值兜底
  return String(mode || "").trim().toUpperCase() || "TOKEN";
}

function buildConfigJson() {
  if (form.billingMode === "TOKEN") {
    return stringifyJson({
      basePrices: {
        input: form.inputUnitPrice,
        output: form.outputUnitPrice,
        cacheRead: form.cacheReadUnitPrice,
        cacheWrite: form.cacheWriteUnitPrice
      }
    });
  }
  if (form.billingMode === "IMAGE") {
    return stringifyJson({
      defaultUnitPrice: form.unitPrice,
      imageResolution: form.imageResolution?.trim() || null,
      imageQuality: form.imageQuality?.trim() || null
    });
  }
  if (form.billingMode === "VIDEO") {
    const resolutionPrices: Dict = {};
    if (form.videoPrice720p != null) {
      resolutionPrices["720P"] = form.videoPrice720p;
    }
    if (form.videoPrice1080p != null) {
      resolutionPrices["1080P"] = form.videoPrice1080p;
    }
    return stringifyJson({
      defaultUnitPrice: form.unitPrice,
      ...(Object.keys(resolutionPrices).length ? {resolutionPrices} : {})
    });
  }
  if (form.billingMode === "EMBEDDING") {
    return stringifyJson({defaultUnitPrice: form.inputUnitPrice});
  }
  if (form.billingMode === "TIERED") {
    const ladder = form.ladderJson?.trim() ? parseJsonObject(form.ladderJson) : null;
    return stringifyJson({
      defaultUnitPrice: form.unitPrice,
      ...(ladder ? {ladder} : {})
    });
  }
  if (form.billingMode === "CUSTOM") {
    return form.customConfigJson?.trim() || null;
  }
  if (form.billingMode === "REQUEST" || form.billingMode === "AUDIO") {
    return stringifyJson({defaultUnitPrice: form.unitPrice});
  }
  return null;
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
    currency: form.currency,
    unit: form.billingMode === "FREE" ? "1K_TOKENS" : form.unit,
    configJson: buildConfigJson(),
    enabled: form.enabled,
    remark: form.remark?.trim() || null
  };
}

async function submit() {
  if (form.billingMode === "TIERED" && !validateJson(form.ladderJson, "阶梯配置")) {
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
