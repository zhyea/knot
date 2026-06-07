<template>
  <el-drawer
    :model-value="modelValue"
    :title="isEdit ? '编辑模型池' : '新建模型池'"
    size="60%"
    class="drawer-with-scrollbar"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >
    <el-scrollbar max-height="calc(100vh - 140px)">
    <el-form :model="form" label-width="100px" class="model-pool-form">
      <div class="slot-body pool-section">
        <div class="section-head">
          <div>
            <h3>基础信息</h3>
            <p>模型池编码提供给路由规则使用，启用前需要至少配置一个启用模型。</p>
          </div>
          <el-form-item label="启用" class="inline-switch">
            <el-switch v-model="form.enabled" />
          </el-form-item>
        </div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模型池编码" required :error="poolCodeError">
              <el-input v-model="form.poolCode" maxlength="64" show-word-limit @blur="validatePoolCode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型类型" required>
              <EnumSelect v-model="form.modelType" category="model_type" @change="onModelTypeChange" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="选择策略" required>
              <el-select v-model="form.selectionStrategy" style="width: 100%">
                <el-option label="权重" value="WEIGHTED" />
                <el-option label="优先级" value="PRIORITY" />
                <el-option label="随机" value="RANDOM" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <div class="space-line" />

      <div class="slot-body pool-section">
        <div class="section-head">
          <div>
            <h3>池内模型</h3>
            <p>按模型类型筛选供应商模型，权重用于加权选择，优先级用于优先级策略。</p>
          </div>
        </div>
        <el-form-item label="绑定模型" required class="bind-block-item">
          <RemoteEntitySelect
            v-model="selectedModelIds"
            :load-function="loadModelOptions"
            :label-function="modelLabel"
            :selected-options="boundModelRows"
            :extra-params="{ modelTypes: form.modelType ? [form.modelType] : [] }"
            placeholder="请选择模型，可多选"
            multiple
            collapse-tags
            collapse-tags-tooltip
            style="width: 100%"
          />
        </el-form-item>
        <el-table
          v-if="boundModelRows.length"
          :data="boundModelRows"
          border
          row-key="modelId"
          class="model-pool-items-table"
        >
          <el-table-column prop="modelCode" label="模型编码" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="bind-list__text">{{ row.modelCode || "—" }}</span>
            </template>
          </el-table-column>
          <el-table-column label="模型名称" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="bind-list__text">{{ row.modelName || row.name || "—" }}</span>
            </template>
          </el-table-column>
          <el-table-column label="供应商" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="bind-list__text">{{ row.providerName || (row.providerId ? `#${row.providerId}` : "—") }}</span>
            </template>
          </el-table-column>
          <el-table-column label="权重" width="150" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.weight" :min="1" :max="10000" class="table-number-input" />
            </template>
          </el-table-column>
          <el-table-column label="优先级" width="150" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.priority" :min="0" :max="9999" class="table-number-input" />
            </template>
          </el-table-column>
          <el-table-column label="启用" width="90" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ row }">
              <el-button link type="danger" @click="removeModel(row.modelId)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
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
import { Delete } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import EnumSelect from "../common/EnumSelect.vue";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import { checkModelPoolCode, createModelPool, updateModelPool } from "../../api/modelPools";
import { listModels } from "../../api/models";
import { mergeOptionList, normalizeOptionList } from "../../utils/options";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  pool: { type: Object, default: null }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const isEdit = computed(() => props.pool != null);
const saving = ref(false);
const poolCodeError = ref("");
const modelOptions = ref([]);

const form = reactive({
  id: null,
  poolCode: "",
  name: "",
  modelType: "CHAT",
  selectionStrategy: "WEIGHTED",
  enabled: false,
  remark: "",
  items: []
});

const selectedModelIds = computed({
  get: () => form.items.map((item) => item.modelId).filter((id) => id != null),
  set: (ids) => onSelectedModelsChange(ids)
});

const boundModelRows = computed(() =>
  form.items.map((item) => {
    const model = modelOptions.value.find((m) => m.id === item.modelId);
    item.id = item.modelId;
    item.modelCode = model?.modelCode || item.modelCode;
    item.modelName = model?.name || item.modelName;
    item.name = model?.name || item.name;
    item.modelType = model?.modelType || item.modelType;
    item.providerId = model?.providerId || item.providerId;
    item.providerName = model?.providerName || item.providerName;
    return item;
  })
);

function modelLabel(model) {
  return model.modelCode ? `${model.name || model.modelCode}（${model.modelCode}）` : `#${model.id}`;
}

function mergeOptions(list) {
  modelOptions.value = mergeOptionList(modelOptions.value, list);
}

async function loadModelOptions(params) {
  const res = await listModels(params);
  const list = normalizeOptionList(res);
  mergeOptions(list);
  return res;
}

function resetForm() {
  const row = props.pool;
  form.id = row?.id ?? null;
  form.poolCode = row?.poolCode || "";
  form.name = row?.name || "";
  form.modelType = row?.modelType || "CHAT";
  form.selectionStrategy = row?.selectionStrategy || "WEIGHTED";
  form.enabled = row?.enabled === true;
  form.remark = row?.remark || "";
  form.items = (row?.items || []).map((item) => ({
    modelId: item.modelId,
    modelCode: item.modelCode,
    modelName: item.modelName,
    modelType: item.modelType,
    providerId: item.providerId,
    providerName: item.providerName,
    weight: item.weight ?? 100,
    priority: item.priority ?? 100,
    enabled: item.enabled !== false
  }));
  poolCodeError.value = "";
}

watch(
  () => [props.modelValue, props.pool],
  ([visible]) => {
    if (visible) {
      resetForm();
      loadModelOptions({ pageNum: 1, pageSize: 10, modelTypes: form.modelType ? [form.modelType] : [] });
    }
  }
);

watch(
  () => form.poolCode,
  () => {
    poolCodeError.value = "";
  }
);

function onModelTypeChange() {
  form.items = [];
  modelOptions.value = [];
  loadModelOptions({ pageNum: 1, pageSize: 10, modelTypes: form.modelType ? [form.modelType] : [] });
}

function onSelectedModelsChange(modelIds) {
  const nextIds = Array.isArray(modelIds) ? modelIds : [];
  const existingById = new Map(form.items.map((item) => [item.modelId, item]));
  form.items = nextIds.map((modelId) => existingById.get(modelId) || {
    modelId,
    weight: 100,
    priority: 100,
    enabled: true
  });
}

function removeModel(modelId) {
  onSelectedModelsChange(form.items.map((item) => item.modelId).filter((id) => id !== modelId));
}

function onClosed() {
  form.id = null;
}

async function validatePoolCode() {
  const code = form.poolCode?.trim();
  if (!code) {
    poolCodeError.value = "请填写模型池编码";
    return false;
  }
  try {
    const res = await checkModelPoolCode(code, isEdit.value ? form.id : null);
    poolCodeError.value = res?.available ? "" : "模型池编码已存在";
    return !!res?.available;
  } catch {
    return false;
  }
}

function buildPayload() {
  return {
    poolCode: form.poolCode?.trim(),
    name: form.name?.trim(),
    modelType: form.modelType,
    selectionStrategy: form.selectionStrategy,
    enabled: form.enabled,
    remark: form.remark?.trim() || null,
    items: form.items.map((item) => ({
      modelId: item.modelId,
      weight: item.weight ?? 100,
      priority: item.priority ?? 100,
      enabled: item.enabled !== false
    }))
  };
}

async function submit() {
  if (!form.name?.trim()) {
    ElMessage.warning("请填写名称");
    return;
  }
  if (!(await validatePoolCode())) {
    return;
  }
  if (form.enabled && !form.items.some((item) => item.enabled !== false)) {
    ElMessage.warning("启用模型池前请至少配置一个启用模型");
    return;
  }
  saving.value = true;
  try {
    const body = buildPayload();
    if (isEdit.value) {
      await updateModelPool(form.id, body);
      ElMessage.success("已保存");
    } else {
      await createModelPool(body);
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
.model-pool-form {
  padding-bottom: 8px;
}

.pool-section {
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

.bind-block-item :deep(.el-form-item__label) {
  float: none;
  display: block;
  width: auto !important;
  margin-bottom: 8px;
  text-align: left;
}

.bind-block-item :deep(.el-form-item__content) {
  display: block;
  margin-left: 0 !important;
}

.model-pool-items-table {
  margin-top: 10px;
  width: 100%;
}

.model-pool-items-table :deep(.el-table__cell) {
  font-size: 12px;
}

.model-pool-items-table :deep(th.el-table__cell) {
  font-size: 12px;
  font-weight: 600;
}

.model-pool-items-table :deep(.cell) {
  padding-left: 12px;
  padding-right: 12px;
}

.table-number-input {
  width: 118px;
}

.table-number-input :deep(.el-input__inner) {
  font-size: 12px;
}

.model-pool-items-table :deep(.el-input-number),
.model-pool-items-table :deep(.el-switch),
.model-pool-items-table :deep(.el-button) {
  font-size: 12px;
}

.bind-list__text {
  overflow: hidden;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

</style>
