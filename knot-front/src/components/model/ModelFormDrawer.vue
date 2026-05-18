<template>
  <el-drawer
      :model-value="modelValue"
      :title="isEdit ? '编辑模型' : '新建模型'"
      size="50%"
      destroy-on-close
      @update:model-value="emit('update:modelValue', $event)"
      @closed="onClosed"
  >
    <el-form v-loading="detailLoading" :model="form" label-width="100px">
      <div class="slot-body">
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
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="模型名称"/>
        </el-form-item>
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
          <el-col :span="12">
            <el-form-item label="模型类型" required>
              <el-select v-model="form.modelType" placeholder="请选择模型类型" style="width: 100%">
                <el-option
                    v-for="item in modelTypeOptions"
                    :key="item.itemCode"
                    :label="item.itemLabel"
                    :value="item.itemCode"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="版本">
              <el-input v-model="form.version" placeholder="如 2024-08-06"/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用">
              <el-switch v-model="form.enabled"/>
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <div class="space-line"/>

      <div class="slot-body">
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
