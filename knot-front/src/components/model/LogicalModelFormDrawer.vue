<template>
  <el-drawer
    :model-value="modelValue"
    :title="isEdit ? '编辑统一模型' : '新建统一模型'"
    size="58%"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >
    <el-form v-loading="detailLoading" :model="form" label-width="110px" class="logical-model-form">
      <div class="slot-body form-section">
        <div class="section-head">
          <div>
            <h3>基础信息</h3>
            <p>对调用方暴露的统一模型名称和基础能力描述。</p>
          </div>
          <el-form-item label="启用" class="inline-switch">
            <el-switch v-model="form.enabled" />
          </el-form-item>
        </div>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模型编码" required :error="modelCodeError">
              <el-input
                v-model="form.modelCode"
                placeholder="如 knot-chat-premium"
                maxlength="128"
                show-word-limit
                :disabled="modelCodeChecking"
                @blur="validateModelCode"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型名称" required>
              <el-input v-model="form.modelName" placeholder="如 Knot Chat Premium" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="模型类型" required>
              <EnumSelect v-model="form.modelType" category="model_type" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="模型族">
              <el-input v-model="form.modelFamily" placeholder="如 omni/general" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="版本">
              <el-input v-model="form.version" placeholder="如 1.0" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="展示名称">
          <el-input v-model="form.displayName" placeholder="模型广场展示名称" />
        </el-form-item>
        <el-form-item label="一句话介绍">
          <el-input v-model="form.tagline" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="模型说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </div>

      <div class="space-line" />

      <div class="slot-body form-section">
        <div class="section-head">
          <div>
            <h3>广场展示</h3>
            <p>用于模型广场检索、推荐和用户选型。</p>
          </div>
        </div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="标签">
              <el-select v-model="form.tags" multiple filterable allow-create default-first-option style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="应用场景">
              <el-select v-model="form.useCases" multiple filterable allow-create default-first-option style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="可见性">
              <EnumSelect v-model="form.visibility" category="logical_model_visibility" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="发布状态">
              <EnumSelect v-model="form.publishStatus" category="logical_model_publish_status" />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="推荐">
              <el-switch v-model="form.featured" />
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <div class="space-line" />

      <div class="slot-body form-section">
        <div class="section-head">
          <div>
            <h3>能力与约束</h3>
            <p>描述统一模型的窗口、模态、语言和质量成本摘要。</p>
          </div>
        </div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="上下文窗口">
              <el-input-number v-model="form.contextWindow" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大输出">
              <el-input-number v-model="form.maxOutputTokens" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="输入模态">
              <el-select v-model="form.inputModalities" multiple filterable allow-create default-first-option style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="输出模态">
              <el-select v-model="form.outputModalities" multiple filterable allow-create default-first-option style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="语言">
              <el-select v-model="form.languages" multiple filterable allow-create default-first-option style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="质量">
              <EnumSelect v-model="form.qualityLevel" category="logical_model_quality_level" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="延迟">
              <EnumSelect v-model="form.latencyLevel" category="logical_model_latency_level" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="成本">
              <EnumSelect v-model="form.costLevel" category="logical_model_cost_level" clearable />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="价格摘要">
          <el-input v-model="form.pricingSummary" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
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
import EnumSelect from "../common/EnumSelect.vue";
import {
  checkLogicalModelCode,
  createLogicalModel,
  getLogicalModel,
  updateLogicalModel
} from "../../api/logicalModels";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  model: { type: Object, default: null }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
const detailLoading = ref(false);
const modelCodeChecking = ref(false);
const modelCodeError = ref("");
const isEdit = computed(() => props.model != null);

const form = reactive(defaultForm());

function defaultForm() {
  return {
    id: null,
    modelCode: "",
    modelName: "",
    modelType: "CHAT",
    modelFamily: "",
    version: "1.0",
    displayName: "",
    tagline: "",
    description: "",
    logoUrl: "",
    coverUrl: "",
    tags: [],
    useCases: [],
    capabilities: {},
    contextWindow: null,
    maxOutputTokens: null,
    inputModalities: ["text"],
    outputModalities: ["text"],
    languages: ["zh-CN"],
    defaultParams: {},
    paramSchema: {},
    safetyPolicy: {},
    visibility: "PUBLIC",
    publishStatus: "DRAFT",
    enabled: true,
    sortOrder: 0,
    featured: false,
    ownerUserId: null,
    ownerTeam: "",
    qualityLevel: "",
    latencyLevel: "",
    costLevel: "",
    pricingSummary: "",
    remark: ""
  };
}

function fillForm(row) {
  Object.assign(form, defaultForm(), row || {});
  form.tags = Array.isArray(row?.tags) ? row.tags : [];
  form.useCases = Array.isArray(row?.useCases) ? row.useCases : [];
  form.inputModalities = Array.isArray(row?.inputModalities) ? row.inputModalities : ["text"];
  form.outputModalities = Array.isArray(row?.outputModalities) ? row.outputModalities : ["text"];
  form.languages = Array.isArray(row?.languages) ? row.languages : ["zh-CN"];
}

watch(
  () => [props.modelValue, props.model],
  async ([visible]) => {
    if (!visible) return;
    modelCodeError.value = "";
    if (props.model?.id) {
      fillForm(props.model);
      detailLoading.value = true;
      try {
        fillForm(await getLogicalModel(props.model.id));
      } finally {
        detailLoading.value = false;
      }
    } else {
      fillForm(null);
    }
  }
);

watch(
  () => form.modelCode,
  () => {
    if (modelCodeError.value) {
      modelCodeError.value = "";
    }
  }
);

function onClosed() {
  fillForm(null);
  modelCodeError.value = "";
}

async function validateModelCode() {
  const code = form.modelCode?.trim();
  if (!code) {
    modelCodeError.value = "请填写模型编码";
    return false;
  }
  modelCodeChecking.value = true;
  try {
    const res = await checkLogicalModelCode(code, isEdit.value ? form.id : null);
    modelCodeError.value = res?.available ? "" : `模型编码 ${code} 已存在`;
    return !!res?.available;
  } finally {
    modelCodeChecking.value = false;
  }
}

function buildPayload() {
  return {
    ...form,
    modelCode: form.modelCode?.trim(),
    modelName: form.modelName?.trim(),
    displayName: form.displayName?.trim() || form.modelName?.trim(),
    modelFamily: form.modelFamily?.trim() || null,
    version: form.version?.trim() || null,
    ownerTeam: form.ownerTeam?.trim() || null,
    qualityLevel: form.qualityLevel || null,
    latencyLevel: form.latencyLevel || null,
    costLevel: form.costLevel || null
  };
}

async function submit() {
  if (!form.modelCode?.trim()) {
    ElMessage.warning("请填写模型编码");
    return;
  }
  if (!form.modelName?.trim()) {
    ElMessage.warning("请填写模型名称");
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
      await updateLogicalModel(form.id, payload);
      ElMessage.success("已保存");
    } else {
      await createLogicalModel(payload);
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
.logical-model-form {
  padding-bottom: 8px;
}

.form-section {
  border-color: var(--knot-border, #e4e7ed);
  box-shadow: 0 8px 24px rgba(31, 45, 61, 0.04);
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--knot-border, #ebeef5);
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
</style>
