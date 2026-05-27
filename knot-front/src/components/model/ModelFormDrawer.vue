<template>
  <el-drawer
      :model-value="modelValue"
      :title="isEdit ? '编辑模型' : '新建模型'"
      size="50%"
      destroy-on-close
      @update:model-value="emit('update:modelValue', $event)"
      @closed="onClosed"
  >
    <el-form v-loading="detailLoading" :model="form" label-width="100px" class="model-form">
      <div class="slot-body model-section">
        <div class="section-head">
          <div>
            <h3>基础信息</h3>
            <p>维护模型编码、展示名称、类型和版本，模型编码用于路由、计费和上游调用定位。</p>
          </div>
          <el-form-item label="启用" class="inline-switch">
            <el-switch v-model="form.enabled"/>
          </el-form-item>
        </div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模型编码" required :error="modelCodeError">
              <el-input
                  v-model="form.modelCode"
                  placeholder="请输入模型编码"
                  maxlength="128"
                  show-word-limit
                  :disabled="modelCodeChecking"
                  @blur="validateModelCode"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="form.name" placeholder="模型名称"/>
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
              <el-input v-model="form.version" placeholder="如 2024-08-06 或 1.0.0"/>
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <div class="space-line"/>

      <div class="slot-body model-section">
        <div class="section-head">
          <div>
            <h3>供应商归属</h3>
            <p>指定模型所属供应商，用于分组管理、凭证匹配和上游调用。</p>
          </div>
        </div>
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
                    v-for="p in providerOptions"
                    :key="p.id"
                    :label="p.name"
                    :value="p.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <div v-if="selectedProvider" class="provider-card">
          <div>
            <span class="meta-label">供应商编码</span>
            <strong>{{ selectedProvider.code || "—" }}</strong>
          </div>
          <div>
            <span class="meta-label">供应商名称</span>
            <strong>{{ selectedProvider.name || "—" }}</strong>
          </div>
          <div>
            <span class="meta-label">供应商类型</span>
            <strong>{{ selectedProvider.type || "—" }}</strong>
          </div>
        </div>
      </div>

      <div class="space-line"/>

      <div class="slot-body model-section">
        <div class="section-head">
          <div>
            <h3>策略配置</h3>
            <p>按需配置模型级频控和额度策略，留空则不在模型维度覆盖。</p>
          </div>
        </div>
        <el-form-item label="频控策略">
          <KvEditor v-model="form.rateLimitPolicy" value-mode="number"/>
        </el-form-item>
        <el-form-item label="额度策略">
          <KvEditor v-model="form.quotaPolicy" value-mode="number"/>
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
import {computed, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import KvEditor from "../common/KvEditor.vue";
import EnumSelect from "../common/EnumSelect.vue";
import {useEnums} from "../../composables/useEnums";
import {
  emptyQuotaPolicy,
  emptyRateLimitPolicy,
  isEmptyQuotaPolicy,
  isEmptyRateLimitPolicy,
  normalizeQuotaPolicy,
  normalizeRateLimitPolicy
} from "../../utils/trafficPolicy";
import {createModel, updateModel, getModel, checkModelCode} from "../../api/models";
import {listProviders} from "../../api/providers";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  model: {type: Object, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const {options: modelTypeOptions, loadOptions: loadModelTypes} = useEnums("model_type");
const providerOptions = ref([]);
const saving = ref(false);
const detailLoading = ref(false);
const modelCodeChecking = ref(false);
const modelCodeError = ref("");
const modelCodeValidated = ref(false);

const MODEL_CODE_MAX_LEN = 128;

const form = reactive({
  id: null,
  modelCode: "",
  name: "",
  providerId: null,
  modelType: "",
  version: "1.0.0",
  enabled: true,
  rateLimitPolicy: emptyRateLimitPolicy(),
  quotaPolicy: emptyQuotaPolicy()
});

const isEdit = computed(() => props.model != null);
const selectedProvider = computed(() =>
    providerOptions.value.find((item) => item.id === form.providerId)
);

async function loadProviders() {
  const data = await listProviders({pageNum: 1, pageSize: 500});
  providerOptions.value = Array.isArray(data?.list) ? data.list : [];
}

function fillForm(row) {
  form.id = row.id;
  form.modelCode = row.modelCode || "";
  form.name = row.name;
  form.providerId = row.providerId ?? null;
  form.modelType = row.modelType || "";
  form.version = row.version || "1.0.0";
  form.enabled = row.enabled !== false;
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

async function resetForm() {
  modelCodeError.value = "";
  modelCodeValidated.value = false;
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
    form.modelType = modelTypeOptions.value[0]?.itemCode || "CHAT";
    form.version = "1.0.0";
    form.enabled = true;
    form.rateLimitPolicy = emptyRateLimitPolicy();
    form.quotaPolicy = emptyQuotaPolicy();
  }
}

watch(
    () => [props.modelValue, props.model],
    async ([visible]) => {
      if (visible) {
        await loadProviders();
        await loadModelTypes();
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

function buildPayload() {
  return {
    modelCode: form.modelCode?.trim(),
    name: form.name,
    providerId: form.providerId,
    modelType: form.modelType,
    version: form.version,
    enabled: form.enabled,
    rateLimitPolicy: isEmptyRateLimitPolicy(form.rateLimitPolicy)
        ? null
        : normalizeRateLimitPolicy(form.rateLimitPolicy),
    quotaPolicy: isEmptyQuotaPolicy(form.quotaPolicy)
        ? null
        : normalizeQuotaPolicy(form.quotaPolicy)
  };
}

async function submit() {
  if (!form.modelCode?.trim()) {
    ElMessage.warning("请填写模型编码");
    return;
  }
  if (!form.name?.trim()) {
    ElMessage.warning("请填写名称");
    return;
  }
  if (!form.providerId) {
    ElMessage.warning("请选择供应商");
    return;
  }
  if (!form.modelType?.trim()) {
    ElMessage.warning("请选择模型类型");
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
  margin: 0;
  color: #303133;
  font-size: 15px;
  font-weight: 600;
}

.section-head p {
  margin: 5px 0 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.inline-switch {
  margin-bottom: 0;
  flex: 0 0 auto;
}

.provider-card {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-left: 100px;
  padding: 12px 14px;
  border: 1px solid var(--knot-border, #ebeef5);
  border-radius: 0;
  background: var(--knot-surface-soft, #f8fafc);
}

.provider-card > div {
  min-width: 0;
}

.provider-card strong {
  display: block;
  margin-top: 4px;
  color: #303133;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-label {
  display: block;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 900px) {
  .section-head {
    display: block;
  }

  .inline-switch {
    margin-top: 12px;
  }

  .provider-card {
    grid-template-columns: 1fr;
    margin-left: 0;
  }
}
</style>
