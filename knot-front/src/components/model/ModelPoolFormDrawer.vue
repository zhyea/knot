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
            <p>模型池编码提供给路由规则使用；池内模型必须全部属于同一个统一模型，启用前需要至少配置一个启用模型。</p>
          </div>
          <el-form-item label="启用" class="inline-switch">
            <el-switch v-model="form.enabled" />
          </el-form-item>
        </div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模型池编码" required :error="poolCodeError">
              <el-input
                v-if="!isEdit"
                v-model="poolSuffix"
                maxlength="59"
                show-word-limit
                placeholder="如 chat-xxx（自动加 pool- 前缀）"
                @blur="validatePoolCode"
              >
                <template #prepend>pool-</template>
              </el-input>
              <el-input
                v-else
                v-model="form.poolCode"
                maxlength="64"
                show-word-limit
                placeholder="如 chat-premium-pool"
                @blur="validatePoolCode"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="统一模型" required>
              <RemoteEntitySelect
                v-model="form.logicalModelCode"
                value-key="modelCode"
                :load-function="loadLogicalModelOptions"
                :label-function="logicalModelLabel"
                :selected-options="selectedLogicalModelOptions"
                placeholder="请选择统一模型"
                style="width: 100%"
                @change="onLogicalModelChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="选择策略" required>
              <EnumControl v-model="form.selectionStrategy" enum-name="ModelPoolSelectionStrategyEnum" />
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
            <p>仅可选择归属于上方统一模型的供应商模型；权重用于加权选择，优先级用于优先级策略。</p>
          </div>
        </div>
        <el-form-item label="绑定模型" required class="bind-block-item">
          <RemoteEntitySelect
            v-model="selectedModelCodes"
            :load-function="loadModelOptions"
            :label-function="modelLabel"
            :selected-options="boundModelRows"
            value-key="modelCode"
            :disabled="!form.logicalModelCode"
            :extra-params="{ logicalModelCode: form.logicalModelCode || undefined }"
            :placeholder="form.logicalModelCode ? '请选择模型，可多选' : '请先选择统一模型'"
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
          row-key="modelCode"
          class="model-pool-items-table"
        >
          <el-table-column prop="modelCode" label="模型编码" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="bind-list__text">{{ row.modelCode || "—" }}</span>
            </template>
          </el-table-column>
          <el-table-column label="供应商" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="bind-list__text">{{ row.providerName || (row.providerAccountCode ? `#${row.providerAccountCode}` : "—") }}</span>
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
              <el-button link type="danger" @click="removeModel(row.modelCode)">
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

<script setup lang="ts">
import type {PropType} from "vue";
import type {Dict, Row} from "@/types";
import {computed, reactive, ref, watch} from "vue";
import {Delete} from "@element-plus/icons-vue";
import {ElMessage} from "element-plus";
import EnumControl from "../common/EnumControl.vue";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import {checkModelPoolCode, createModelPool, updateModelPool} from "@/api/modelPools";
import {listModels} from "@/api/models";
import {listLogicalModels} from "@/api/logicalModels";
import {mergeOptionList, normalizeOptionList, resolveSelectedOption} from "@/utils/options";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  pool: { type: Object as PropType<Dict | null>, default: null },
  /** 统一模型下拉仅展示 modelCode */
  codeOnly: { type: Boolean, default: false }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const isEdit = computed(() => props.pool != null);
const saving = ref(false);
const poolCodeError = ref("");
const poolSuffix = ref("");
const modelOptions = ref<Row[]>([]);
const logicalModelOptions = ref<Row[]>([]);

interface PoolItemForm {
  id?: number | string | null;
  modelCode: string;
  modelName?: string;
  name?: string;
  modelType?: string;
  providerAccountCode?: string;
  providerName?: string;
  weight: number;
  priority: number;
  enabled: boolean;
}

interface PoolFormState {
  id: number | string | null;
  poolCode: string;
  name: string;
  logicalModelCode: string | null;
  logicalModelName?: string;
  modelType?: string;
  selectionStrategy: string;
  enabled: boolean;
  remark: string;
  items: PoolItemForm[];
}

const form = reactive<PoolFormState>({
  id: null,
  poolCode: "",
  name: "",
  logicalModelCode: null,
  selectionStrategy: "WEIGHTED",
  enabled: false,
  remark: "",
  items: []
});

const selectedLogicalModelOptions = computed(() =>
  resolveSelectedOption(form.logicalModelCode, logicalModelOptions.value, {
    modelCode: form.logicalModelCode,
    modelName: form.logicalModelName
  }, "modelCode")
);

const selectedModelCodes = computed({
  get: () => form.items.map((item) => item.modelCode).filter((code) => code != null),
  set: (codes) => onSelectedModelsChange(codes)
});

const boundModelRows = computed(() =>
  form.items.map((item) => {
    const model = modelOptions.value.find((m) => m.modelCode === item.modelCode);
    item.id = model?.id ?? item.id;
    item.modelCode = model?.modelCode || item.modelCode;
    item.modelName = model?.name || item.modelName;
    item.name = model?.name || item.name;
    item.modelType = model?.modelType || item.modelType;
    item.providerAccountCode = model?.providerAccountCode || item.providerAccountCode;
    item.providerName = model?.providerName || item.providerName;
    return item;
  })
);

function logicalModelLabel(model: Row) {
  return model.modelCode ? `${model.modelName || model.modelCode}（${model.modelCode}）` : `#${model.id}`;
}

function modelLabel(model: Row) {
  return model.modelCode ? `${model.name || model.modelCode}（${model.modelCode}）` : `#${model.id}`;
}

function mergeOptions(list: Row[]) {
  modelOptions.value = mergeOptionList(modelOptions.value, list);
}

async function loadModelOptions(params: Dict) {
  if (!form.logicalModelCode) {
    return {list: [], total: 0};
  }
  const res = await listModels({...params, logicalModelCode: form.logicalModelCode});
  const list = normalizeOptionList(res);
  mergeOptions(list);
  return res;
}

async function loadLogicalModelOptions(params: Dict) {
  const res = await listLogicalModels(params);
  logicalModelOptions.value = mergeOptionList(logicalModelOptions.value, normalizeOptionList(res));
  return res;
}

function resetForm() {
  const row = props.pool;
  form.id = row?.id ?? null;
  form.poolCode = row?.poolCode || "";
  form.name = row?.name || "";
  form.logicalModelCode = row?.logicalModelCode || null;
  form.logicalModelName = row?.logicalModelName;
  form.modelType = row?.modelType;
  form.selectionStrategy = row?.selectionStrategy || "WEIGHTED";
  form.enabled = row?.enabled === true;
  form.remark = row?.remark || "";
  form.items = (row?.items || []).map((item: Row) => ({
    id: item.id ?? null,
    modelCode: item.modelCode,
    modelName: item.modelName,
    modelType: item.modelType,
    providerAccountCode: item.providerAccountCode,
    providerName: item.providerName,
    weight: item.weight ?? 100,
    priority: item.priority ?? 100,
    enabled: item.enabled !== false
  }));
  poolCodeError.value = "";
  poolSuffix.value = "";
}

watch(
  () => [props.modelValue, props.pool],
  ([visible]) => {
    if (visible) {
      resetForm();
      modelOptions.value = [];
      if (form.logicalModelCode) {
        loadModelOptions({pageNum: 1, pageSize: 10});
      }
    }
  }
);

watch(
  () => [form.poolCode, poolSuffix.value],
  () => {
    poolCodeError.value = "";
  }
);

/** 切换统一模型：池内模型必须全部归属新统一模型，故清空已选并清空候选缓存 */
function onLogicalModelChange() {
  form.items = [];
  modelOptions.value = [];
  if (form.logicalModelCode) {
    loadModelOptions({pageNum: 1, pageSize: 10});
  }
}

function onSelectedModelsChange(codes: string[]) {
  const nextCodes = Array.isArray(codes) ? codes : [];
  const existingByCode = new Map(form.items.map((item) => [item.modelCode, item]));
  form.items = nextCodes.map((modelCode) => existingByCode.get(modelCode) || {
    modelCode,
    weight: 100,
    priority: 100,
    enabled: true
  });
}

function removeModel(modelCode: string) {
  onSelectedModelsChange(form.items.map((item) => item.modelCode).filter((code) => code !== modelCode));
}

function onClosed() {
  form.id = null;
}

/** 组装提交用的完整编码：新建池锁定 pool- 前缀 + 后缀；编辑既有池用原始完整编码 */
function buildPoolCode(): string {
  const suffix = (poolSuffix.value || "").trim();
  return isEdit.value ? (form.poolCode?.trim() || "") : `pool-${suffix}`;
}

async function validatePoolCode() {
  const code = buildPoolCode();
  const missing = isEdit.value ? !form.poolCode?.trim() : !poolSuffix.value?.trim();
  if (missing) {
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
    poolCode: buildPoolCode(),
    name: form.name?.trim(),
    logicalModelCode: form.logicalModelCode,
    selectionStrategy: form.selectionStrategy,
    enabled: form.enabled,
    remark: form.remark?.trim() || null,
    items: form.items.map((item) => ({
      modelCode: item.modelCode,
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
  if (!form.logicalModelCode) {
    ElMessage.warning("请选择统一模型");
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
      await updateModelPool(form.id!, body);
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
