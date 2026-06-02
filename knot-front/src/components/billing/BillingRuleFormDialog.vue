<template>
  <el-drawer
    v-model="visible"
    :title="isEdit ? '编辑计费规则' : '新建计费规则'"
    direction="rtl"
    size="55%"
    destroy-on-close
  >
    <el-form :model="form" label-width="118px" class="billing-rule-form">
      <div class="slot-body form-section">
        <div class="section-head">
          <h3>基础信息</h3>
          <el-form-item label="启用" class="inline-switch">
            <el-switch v-model="form.enabled" />
          </el-form-item>
        </div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="规则编码" required>
              <el-input v-model="form.code" :disabled="isEdit" placeholder="如 OPENAI_GPT4O" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规则名称" required>
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商">
              <RemoteEntitySelect
                v-model="form.providerId"
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
                v-model="form.logicalModelId"
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
              <EnumSelect v-model="form.currency" category="billing_currency" />
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <div class="space-line" />

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
                :include-codes="billingModeCodes"
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
        <component :is="modeComponent" :form="form" />
      </div>

      <div class="space-line" />

      <div class="slot-body form-section">
        <div class="section-head">
          <h3>备注</h3>
        </div>
        <el-form-item label-width="0">
          <el-input v-model="form.remark" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
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
import { createBillingRule, updateBillingRule } from "../../api/billing";
import { listProviders } from "../../api/providers";
import { listLogicalModels } from "../../api/logicalModels";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  rule: { type: Object, default: null }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const billingModeCodes = ["TOKEN", "REQUEST", "IMAGE", "AUDIO", "VIDEO", "EMBEDDING", "TIERED", "FREE", "CUSTOM"];
const unitsByMode = {
  DEFAULT: ["PER_TOKEN", "1K_TOKENS", "1M_TOKENS", "PER_REQUEST", "PER_IMAGE", "PER_MINUTE", "PER_SECOND"],
  TOKEN: ["PER_TOKEN", "1K_TOKENS", "1M_TOKENS"],
  REQUEST: ["PER_REQUEST"],
  IMAGE: ["PER_IMAGE"],
  AUDIO: ["PER_MINUTE"],
  VIDEO: ["PER_SECOND"],
  EMBEDDING: ["PER_TOKEN", "1K_TOKENS", "1M_TOKENS"],
  TIERED: ["PER_TOKEN", "1K_TOKENS", "1M_TOKENS"],
  FREE: ["1K_TOKENS"],
  CUSTOM: ["PER_TOKEN", "1K_TOKENS", "1M_TOKENS", "PER_REQUEST", "PER_IMAGE", "PER_MINUTE", "PER_SECOND"]
};
const defaultsByMode = {
  TOKEN: { itemType: "INPUT_TOKEN", unit: "1K_TOKENS" },
  REQUEST: { itemType: "REQUEST", unit: "PER_REQUEST" },
  IMAGE: { itemType: "IMAGE", unit: "PER_IMAGE" },
  AUDIO: { itemType: "AUDIO_MINUTE", unit: "PER_MINUTE" },
  VIDEO: { itemType: "VIDEO_SECOND", unit: "PER_SECOND" },
  EMBEDDING: { itemType: "EMBEDDING_TOKEN", unit: "1K_TOKENS" },
  TIERED: { itemType: "TIERED_USAGE", unit: "1K_TOKENS" },
  FREE: { itemType: "FREE", unit: "1K_TOKENS" },
  CUSTOM: { itemType: "CUSTOM", unit: "1K_TOKENS" }
};
const componentsByMode = {
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
const providerOptions = ref([]);
const logicalModelOptions = ref([]);
const form = reactive({
  id: null,
  code: "",
  name: "",
  providerId: null,
  logicalModelId: null,
  billingMode: "TOKEN",
  currency: "USD",
  itemType: "INPUT_TOKEN",
  unit: "1K_TOKENS",
  unitPrice: 0.002,
  inputUnitPrice: 0.002,
  outputUnitPrice: 0.002,
  cacheReadUnitPrice: 0,
  cacheWriteUnitPrice: 0,
  imageResolution: "",
  imageQuality: "",
  ladderJson: "",
  customConfigJson: "",
  enabled: true,
  remark: ""
});

const isEdit = computed(() => props.rule != null);
const unitCodes = computed(() => unitsByMode[form.billingMode] || unitsByMode.DEFAULT);
const modeComponent = computed(() => componentsByMode[form.billingMode] || BillingModeTokenConfig);
const selectedProviderOptions = computed(() => {
  const item = providerOptions.value.find((p) => p.id === form.providerId);
  if (item) return [item];
  return form.providerId ? [{ id: form.providerId, name: props.rule?.providerName }] : [];
});
const selectedLogicalModelOptions = computed(() => {
  const item = logicalModelOptions.value.find((m) => m.id === form.logicalModelId);
  if (item) return [item];
  return form.logicalModelId ? [{ id: form.logicalModelId, modelName: props.rule?.logicalModelName }] : [];
});

watch(
  () => [props.modelValue, props.rule],
  async ([value]) => {
    if (value) {
      resetForm();
      await Promise.all([loadProviders(), loadLogicalModels()]);
    }
  }
);

watch(
  () => form.billingMode,
  (mode) => applyModeDefaults(mode)
);

function resetForm() {
  const row = props.rule;
  const config = parseJsonObject(row?.configJson);
  form.id = row?.id ?? null;
  form.code = row?.code || "";
  form.name = row?.name || "";
  form.providerId = row?.providerId ?? null;
  form.logicalModelId = row?.logicalModelId ?? null;
  form.billingMode = normalizeMode(row?.billingMode || "TOKEN");
  form.currency = row?.currency || "USD";
  form.itemType = row?.itemType || defaultsByMode[form.billingMode]?.itemType || "INPUT_TOKEN";
  form.unit = row?.unit || defaultsByMode[form.billingMode]?.unit || "1K_TOKENS";
  form.unitPrice = Number(row?.unitPrice ?? 0.002);
  form.inputUnitPrice = Number(config.inputUnitPrice ?? row?.unitPrice ?? 0.002);
  form.outputUnitPrice = Number(config.outputUnitPrice ?? row?.unitPrice ?? 0.002);
  form.cacheReadUnitPrice = Number(config.cacheReadUnitPrice ?? 0);
  form.cacheWriteUnitPrice = Number(config.cacheWriteUnitPrice ?? 0);
  form.imageResolution = config.imageResolution || "";
  form.imageQuality = config.imageQuality || "";
  form.ladderJson = row?.ladderJson || "";
  form.customConfigJson = form.billingMode === "CUSTOM" ? row?.configJson || "" : "";
  form.enabled = row?.enabled !== false;
  form.remark = row?.remark || "";
  applyModeDefaults(form.billingMode, false);
}

function applyModeDefaults(mode, resetPrice = true) {
  const defaults = defaultsByMode[mode] || defaultsByMode.TOKEN;
  form.itemType = defaults.itemType;
  if (!unitCodes.value.includes(form.unit)) {
    form.unit = defaults.unit;
  }
  if (mode === "FREE") {
    form.unitPrice = 0;
  }
  if (resetPrice && mode === "EMBEDDING") {
    form.unitPrice = form.inputUnitPrice;
  }
}

async function loadProviders(params = { pageNum: 1, pageSize: 10 }) {
  const res = await listProviders(params);
  mergeOptions(providerOptions, Array.isArray(res?.list) ? res.list : []);
  return res;
}

async function loadLogicalModels(params = { pageNum: 1, pageSize: 10 }) {
  const res = await listLogicalModels(params);
  mergeOptions(logicalModelOptions, Array.isArray(res?.list) ? res.list : []);
  return res;
}

function mergeOptions(targetRef, list) {
  const map = new Map(targetRef.value.map((item) => [item.id, item]));
  list.forEach((item) => item?.id != null && map.set(item.id, item));
  targetRef.value = Array.from(map.values());
}

function providerLabel(provider) {
  return provider.name || provider.code || `#${provider.id}`;
}

function logicalModelLabel(model) {
  const name = model.displayName || model.modelName || model.modelCode || `#${model.id}`;
  return model.modelCode ? `${name} (${model.modelCode})` : name;
}

function parseJsonObject(value) {
  if (!value) return {};
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function validateJson(value, label) {
  if (!value?.trim()) return true;
  try {
    JSON.parse(value);
    return true;
  } catch {
    ElMessage.warning(`${label}不是合法 JSON`);
    return false;
  }
}

function normalizeMode(mode) {
  const value = String(mode || "").trim().toUpperCase();
  return billingModeCodes.includes(value) ? value : "TOKEN";
}

function buildConfigJson() {
  if (form.billingMode === "TOKEN") {
    return JSON.stringify({
      inputUnitPrice: form.inputUnitPrice,
      outputUnitPrice: form.outputUnitPrice,
      cacheReadUnitPrice: form.cacheReadUnitPrice,
      cacheWriteUnitPrice: form.cacheWriteUnitPrice
    });
  }
  if (form.billingMode === "IMAGE") {
    return JSON.stringify({
      imageResolution: form.imageResolution?.trim() || null,
      imageQuality: form.imageQuality?.trim() || null
    });
  }
  if (form.billingMode === "EMBEDDING") {
    return JSON.stringify({ inputUnitPrice: form.inputUnitPrice });
  }
  if (form.billingMode === "CUSTOM") {
    return form.customConfigJson?.trim() || null;
  }
  return null;
}

function primaryUnitPrice() {
  if (form.billingMode === "FREE" || form.billingMode === "CUSTOM") return 0;
  if (form.billingMode === "TOKEN" || form.billingMode === "EMBEDDING") return form.inputUnitPrice;
  return form.unitPrice;
}

function buildPayload() {
  const tiered = form.billingMode === "TIERED";
  return {
    code: form.code.trim(),
    name: form.name.trim(),
    providerId: form.providerId,
    logicalModelId: form.logicalModelId,
    billingMode: form.billingMode,
    currency: form.currency,
    itemType: form.itemType,
    unit: form.billingMode === "FREE" ? "1K_TOKENS" : form.unit,
    unitPrice: primaryUnitPrice(),
    configJson: buildConfigJson(),
    ladderJson: tiered ? form.ladderJson?.trim() || null : null,
    enabled: form.enabled,
    remark: form.remark?.trim() || null
  };
}

async function submit() {
  if (!form.code?.trim() || !form.name?.trim()) {
    ElMessage.warning("请填写规则编码与名称");
    return;
  }
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
      await updateBillingRule(form.id, payload);
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
