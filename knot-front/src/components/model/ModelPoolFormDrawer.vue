<template>
  <el-drawer
    :model-value="modelValue"
    :title="isEdit ? '编辑模型池' : '新建模型池'"
    size="52%"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >
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
        <div v-if="boundModelRows.length" class="bind-list model-pool-items">
          <div class="bind-list__row bind-list__header model-pool-items__row">
            <span>模型编码</span>
            <span>模型名称</span>
            <span>供应商</span>
            <span>权重</span>
            <span>优先级</span>
            <span>启用</span>
            <span>操作</span>
          </div>
          <div v-for="row in boundModelRows" :key="row.modelId" class="bind-list__row model-pool-items__row">
            <span class="bind-list__text">{{ row.modelCode || "—" }}</span>
            <span class="bind-list__text">{{ row.modelName || row.name || "—" }}</span>
            <span class="bind-list__text">{{ row.providerName || (row.providerId ? `#${row.providerId}` : "—") }}</span>
            <span><el-input-number v-model="row.weight" :min="1" :max="10000" /></span>
            <span><el-input-number v-model="row.priority" :min="0" :max="9999" /></span>
            <span><el-switch v-model="row.enabled" /></span>
            <span>
              <el-button link type="danger" @click="removeModel(row.modelId)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </span>
          </div>
        </div>
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
import { Delete } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import EnumSelect from "../common/EnumSelect.vue";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import { checkModelPoolCode, createModelPool, updateModelPool } from "../../api/modelPools";
import { listModels } from "../../api/models";

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
  const map = new Map(modelOptions.value.map((item) => [item.id, item]));
  for (const item of list) {
    if (item?.id != null) {
      map.set(item.id, item);
    }
  }
  modelOptions.value = Array.from(map.values());
}

async function loadModelOptions(params) {
  const res = await listModels(params);
  const list = Array.isArray(res?.list) ? res.list : [];
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
  font-weight: 600;
  background: var(--knot-surface-soft, #f8fafc);
}

.bind-list__row > span {
  min-width: 0;
  height: 100%;
  padding: 8px 12px;
  border-right: 1px solid var(--knot-border, #ebeef5);
  background: #f3f6fa;
  display: flex;
  align-items: center;
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
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-pool-items__row {
  grid-template-columns: minmax(150px, 1fr) minmax(160px, 1fr) minmax(120px, 0.8fr) 150px 150px 90px 70px;
}

.model-pool-items__row > span:nth-child(n + 4) {
  justify-content: center;
}
</style>
