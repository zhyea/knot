<template>
  <el-drawer
    :model-value="modelValue"
    :title="isEdit ? '编辑供应商模型' : '新建供应商模型'"
    size="50%"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >
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

        <el-row :gutter="16">
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

        <el-row :gutter="16">
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
            <h3>绑定统一模型</h3>
            <p>供应商模型必须绑定模型广场中的统一模型，调用方只感知统一模型。</p>
          </div>
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
        <div v-if="selectedLogicalModel" class="bind-list logical-model-bind-list">
          <div class="bind-list__row bind-list__header logical-model-bind-list__row">
            <span>统一模型编码</span>
            <span>统一模型名称</span>
            <span>模型类型</span>
            <span>模型族</span>
            <span>是否启用</span>
          </div>
          <div class="bind-list__row logical-model-bind-list__row">
            <span class="bind-list__text">{{ selectedLogicalModel.modelCode || "—" }}</span>
            <span class="bind-list__text">{{ logicalModelName(selectedLogicalModel) }}</span>
            <span class="bind-list__text">{{ selectedLogicalModel.modelType || "—" }}</span>
            <span class="bind-list__text">{{ selectedLogicalModel.modelFamily || "—" }}</span>
            <span>
              <el-tag size="small" :type="selectedLogicalModel.enabled === false ? 'info' : 'success'">
                {{ selectedLogicalModel.enabled === false ? "停用" : "启用" }}
              </el-tag>
            </span>
          </div>
        </div>

        <div class="bind-subsection">
          <div class="bind-subsection__head">
            <h3>绑定计费规则</h3>
            <p>根据当前供应商和统一模型筛选成本规则，供应商模型必须绑定计费规则。</p>
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
          <div v-if="selectedBillingRule" class="bind-list billing-rule-bind-list">
            <div class="bind-list__row bind-list__header billing-rule-bind-list__row">
              <span>规则编码</span>
              <span>规则名称</span>
              <span>版本</span>
              <span>是否启用</span>
            </div>
            <div class="bind-list__row billing-rule-bind-list__row">
              <span class="bind-list__text">{{ selectedBillingRule.code || "—" }}</span>
              <span class="bind-list__text">{{ selectedBillingRule.name || "—" }}</span>
              <span class="bind-list__text">v{{ selectedBillingRule.versionNo || 1 }}</span>
              <span>
                <el-tag size="small" :type="selectedBillingRule.enabled === false ? 'info' : 'success'">
                  {{ selectedBillingRule.enabled === false ? "停用" : "启用" }}
                </el-tag>
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="space-line" />

      <div class="slot-body model-section">
        <div class="section-head">
          <div>
            <h3>绑定供应商</h3>
            <p>选择模型所属供应商，用于凭证、协议和调用地址定位。</p>
          </div>
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
        <div v-if="selectedProvider" class="bind-list provider-bind-list">
          <div class="bind-list__row bind-list__header provider-bind-list__row">
            <span>供应商编码</span>
            <span>供应商名称</span>
            <span>是否启用</span>
          </div>
          <div class="bind-list__row provider-bind-list__row">
            <span class="bind-list__text">{{ selectedProvider.code || "—" }}</span>
            <span class="bind-list__text">{{ selectedProvider.name || "—" }}</span>
            <span>
              <el-tag size="small" :type="selectedProvider.enabled === false ? 'info' : 'success'">
                {{ selectedProvider.enabled === false ? "停用" : "启用" }}
              </el-tag>
            </span>
          </div>
        </div>
      </div>

      <div class="space-line" />

      <div class="slot-body model-section">
        <div class="section-head">
          <div>
            <h3>策略配置</h3>
            <p>按需配置模型级频控和额度策略；留空则不在模型维度覆盖。</p>
          </div>
        </div>
        <el-form-item label="频控策略">
          <KvEditor v-model="form.rateLimitPolicy" value-mode="number" />
        </el-form-item>
        <el-form-item label="额度策略">
          <KvEditor v-model="form.quotaPolicy" value-mode="number" />
        </el-form-item>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import KvEditor from "../common/KvEditor.vue";
import EnumSelect from "../common/EnumSelect.vue";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import { useEnums } from "../../composables/useEnums";
import {
  emptyQuotaPolicy,
  emptyRateLimitPolicy,
  isEmptyQuotaPolicy,
  isEmptyRateLimitPolicy,
  normalizeQuotaPolicy,
  normalizeRateLimitPolicy
} from "../../utils/trafficPolicy";
import { checkModelCode, createModel, getModel, updateModel } from "../../api/models";
import { listLogicalModels } from "../../api/logicalModels";
import { listProviders } from "../../api/providers";
import { listBillingRules } from "../../api/billing";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  model: { type: Object, default: null }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const { options: modelTypeOptions, loadOptions: loadModelTypes } = useEnums("model_type");
const providerOptions = ref([]);
const logicalModelOptions = ref([]);
const billingRuleOptions = ref([]);
const saving = ref(false);
const detailLoading = ref(false);
const modelCodeChecking = ref(false);
const modelCodeError = ref("");
const modelCodeValidated = ref(false);
const resettingForm = ref(false);

const MODEL_CODE_MAX_LEN = 128;

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
  quotaPolicy: emptyQuotaPolicy()
});

const isEdit = computed(() => props.model != null);
const selectedProvider = computed(() => providerOptions.value.find((item) => item.id === form.providerId));
const selectedLogicalModel = computed(() => logicalModelOptions.value.find((item) => item.id === form.logicalModelId));
const selectedBillingRule = computed(() => billingRuleOptions.value.find((item) => item.id === form.billingRuleId));
const selectedProviderOptions = computed(() => {
  if (!form.providerId) return [];
  return selectedProvider.value ? [selectedProvider.value] : [{ id: form.providerId, name: props.model?.providerName }];
});
const selectedLogicalModelOptions = computed(() => {
  if (!form.logicalModelId) return [];
  return selectedLogicalModel.value ? [selectedLogicalModel.value] : [{ id: form.logicalModelId }];
});
const selectedBillingRuleOptions = computed(() => {
  if (!form.billingRuleId) return [];
  return selectedBillingRule.value ? [selectedBillingRule.value] : [{ id: form.billingRuleId, name: props.model?.billingRuleName }];
});
const billingRuleFilterParams = computed(() => ({
  providerId: form.providerId ?? undefined,
  logicalModelId: form.logicalModelId ?? undefined
}));

async function loadProviders(params = { pageNum: 1, pageSize: 10 }) {
  const data = await listProviders(params);
  mergeOptions(providerOptions, Array.isArray(data?.list) ? data.list : []);
  return data;
}

async function loadLogicalModels(params = { pageNum: 1, pageSize: 10 }) {
  const data = await listLogicalModels(params);
  mergeOptions(logicalModelOptions, Array.isArray(data?.list) ? data.list : []);
  return data;
}

async function loadBillingRules(params = { pageNum: 1, pageSize: 10 }) {
  const data = await listBillingRules({
    ...params,
    providerId: params.providerId ?? form.providerId ?? undefined,
    logicalModelId: params.logicalModelId ?? form.logicalModelId ?? undefined
  });
  mergeOptions(billingRuleOptions, Array.isArray(data?.list) ? data.list : []);
  return data;
}

function mergeOptions(targetRef, list) {
  const map = new Map(targetRef.value.map((item) => [item.id, item]));
  for (const item of list) {
    if (item?.id != null) {
      map.set(item.id, item);
    }
  }
  targetRef.value = Array.from(map.values());
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

function billingRuleLabel(rule) {
  return rule.code ? `${rule.name || rule.code} (${rule.code}, v${rule.versionNo || 1})` : `#${rule.id}`;
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

async function resetForm() {
  resettingForm.value = true;
  modelCodeError.value = "";
  modelCodeValidated.value = false;
  try {
    if (props.model) {
      fillForm(props.model);
      if (props.model.id) {
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
    }
  } finally {
    resettingForm.value = false;
  }
}

watch(
  () => [props.modelValue, props.model],
  async ([visible]) => {
    if (visible) {
      await Promise.all([loadProviders(), loadLogicalModels(), loadBillingRules(), loadModelTypes()]);
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
  return !failed;
}

function beforeEnableChange() {
  if (!form.enabled && !validateRequired(true)) {
    return false;
  }
  return true;
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
    quotaPolicy: isEmptyQuotaPolicy(form.quotaPolicy) ? null : normalizeQuotaPolicy(form.quotaPolicy)
  };
}

async function submit() {
  if (!validateRequired(true)) {
    return;
  }
  if (!(await validateModelCode())) {
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

.bind-list {
  margin-top: 10px;
  width: 100%;
  border: 1px solid var(--knot-border, #ebeef5);
  background: var(--knot-surface-soft, #f8fafc);
}

.bind-list__row {
  display: grid;
  align-items: center;
  min-height: 48px;
  border-bottom: 1px solid var(--knot-border, #ebeef5);
  background: var(--knot-surface, #fff);
  font-size: 12px;
}

.bind-list__row:last-child {
  border-bottom: 0;
}

.bind-list__header {
  min-height: 36px;
  color: #606266;
  font-size: 12px;
  font-weight: 600;
  background: var(--knot-surface-soft, #f8fafc);
}

.bind-list__row > span {
  display: flex;
  align-items: center;
  min-width: 0;
  height: 100%;
  padding: 8px 12px;
  border-right: 1px solid var(--knot-border, #ebeef5);
  background: #f3f6fa;
}

.bind-list__header > span {
  background: var(--knot-surface-soft, #f8fafc);
}

.bind-list__row > span:last-child {
  border-right: 0;
}

.bind-list__text {
  overflow: hidden;
  color: #303133;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bind-subsection {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--knot-border, #ebeef5);
}

.bind-subsection__head {
  margin-bottom: 12px;
}

.bind-subsection__head h3 {
  margin: 0 0 4px;
  color: var(--knot-text, #303133);
  font-size: 14px;
  font-weight: 600;
}

.bind-subsection__head p {
  margin: 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.provider-bind-list__row {
  grid-template-columns: minmax(160px, 1fr) minmax(160px, 1fr) 100px;
}

.logical-model-bind-list__row {
  grid-template-columns: minmax(150px, 1fr) minmax(160px, 1fr) 100px 120px 100px;
}

.billing-rule-bind-list__row {
  grid-template-columns: minmax(150px, 1fr) minmax(180px, 1fr) 80px 90px;
}

.provider-bind-list__row > span:nth-child(3),
.logical-model-bind-list__row > span:nth-child(5),
.billing-rule-bind-list__row > span:nth-child(4) {
  justify-content: center;
}

@media (max-width: 900px) {
  .section-head {
    display: block;
  }

  .inline-switch {
    margin-top: 12px;
  }

  .bind-list__header {
    display: none;
  }

  .provider-bind-list__row,
  .logical-model-bind-list__row,
  .billing-rule-bind-list__row {
    grid-template-columns: 1fr;
    row-gap: 8px;
  }

  .provider-bind-list__row > span:nth-child(3),
  .logical-model-bind-list__row > span:nth-child(5),
  .billing-rule-bind-list__row > span:nth-child(4) {
    justify-content: flex-start;
  }
}
</style>
