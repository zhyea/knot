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
                :disabled="codeLocked"
                @blur="validatePoolCode"
              />
              <div v-if="codeLocked" class="form-tip">{{ codeLockHint }}</div>
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
                :load-function="loadLogicalModelOptions"
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
            <el-form-item label="粘性路由">
              <div class="sticky-row">
                <el-switch v-model="form.stickyPolicy.enabled" />
                <el-input
                  v-model="form.stickyPolicy.headerName"
                  :disabled="!form.stickyPolicy.enabled"
                  maxlength="128"
                  show-word-limit
                  placeholder="粘性键请求头"
                  class="sticky-header-input"
                />
                <span class="sticky-hint">
                  开启后，携带同一粘性键的请求会稳定落到池内同一个模型，用于命中上游 prompt cache；
                  未传该请求头时仍按选择策略分配。
                </span>
              </div>
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
              <span class="bind-list__text">{{ displayText(row.modelCode) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="供应商" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="bind-list__text">{{ displayText(row.providerName || (row.providerAccountCode ? `#${row.providerAccountCode}` : "")) }}</span>
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
import {displayText} from "@/utils/format";
import {computed, reactive, ref, watch} from "vue";
import {Delete} from "@element-plus/icons-vue";
import {ElMessage} from "element-plus";
import EnumControl from "../common/EnumControl.vue";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import {checkModelPoolCode, createModelPool, updateModelPool} from "@/api/modelPools";
import {listLogicalModelOptions, listModelOptions} from "@/api/options";
import {defaultStickyPolicy, normalizeStickyPolicy, type StickyPolicy} from "@/utils/stickyPolicy";
import {resolveSelectedOption, toOptionsLoader} from "@/utils/options";
import {useMissingOptionGuard} from "@/composables/useMissingOptionGuard";
import {useCodeBindingLock} from "@/composables/useCodeBindingLock";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  pool: { type: Object as PropType<Dict | null>, default: null }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const isEdit = computed(() => props.pool != null);
/** 编码引用处数（池内模型条目 + 路由规则目标）：后端在列表/详情里下发，>0 时编码不可改 */
const boundCount = ref(0);
const {codeLocked, codeLockHint} = useCodeBindingLock(() => isEdit.value, () => boundCount.value);
const saving = ref(false);
const poolCodeError = ref("");
const poolSuffix = ref("");
const modelOptions = ref<Row[]>([]);
const logicalModelOptions = ref<Row[]>([]);

interface PoolItemForm {
  id?: number | string | null;
  modelCode: string;
  /** 候选项的取值字段（= modelCode）；下拉固定按 value 匹配（options 契约） */
  value?: string;
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
  stickyPolicy: StickyPolicy;
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
  stickyPolicy: defaultStickyPolicy(),
  enabled: false,
  remark: "",
  items: []
});

const selectedLogicalModelOptions = computed(() =>
  resolveSelectedOption(form.logicalModelCode, logicalModelOptions.value, {
    value: form.logicalModelCode,
    label: form.logicalModelName
  })
);

const selectedModelCodes = computed({
  get: () => form.items.map((item) => item.modelCode).filter((code) => code != null),
  set: (codes) => onSelectedModelsChange(codes)
});

const boundModelRows = computed(() =>
  form.items.map((item) => {
    const model = modelOptions.value.find((m) => m.value === item.modelCode);
    const meta = (model?.meta as Row) ?? {};
    item.value = item.modelCode;
    item.modelType = meta.modelType || item.modelType;
    item.providerAccountCode = meta.providerAccountCode || item.providerAccountCode;
    item.providerName = meta.providerName || item.providerName;
    return item;
  })
);

const loadModelOptions = toOptionsLoader(listModelOptions, modelOptions);
const loadLogicalModelOptions = toOptionsLoader(listLogicalModelOptions, logicalModelOptions);

function resetForm() {
  const row = props.pool;
  form.id = row?.id ?? null;
  form.poolCode = row?.poolCode || "";
  form.name = row?.name || "";
  form.logicalModelCode = row?.logicalModelCode || null;
  form.logicalModelName = row?.logicalModelName;
  form.modelType = row?.modelType;
  form.selectionStrategy = row?.selectionStrategy || "WEIGHTED";
  form.stickyPolicy = normalizeStickyPolicy(row?.stickyPolicy);
  form.enabled = row?.enabled === true;
  form.remark = row?.remark || "";
  form.items = (row?.items || []).map((item: Row) => ({
    id: item.id ?? null,
    modelCode: item.modelCode,
    modelType: item.modelType,
    providerAccountCode: item.providerAccountCode,
    providerName: item.providerName,
    weight: item.weight ?? 100,
    priority: item.priority ?? 100,
    enabled: item.enabled !== false
  }));
  poolCodeError.value = "";
  poolSuffix.value = "";
  boundCount.value = Number(row?.boundCount ?? 0);
}

watch(
  () => [props.modelValue, props.pool],
  ([visible]) => {
    if (visible) {
      resetForm();
      modelOptions.value = [];
      if (form.logicalModelCode) {
        loadModelOptions({values: form.items.map((i) => i.modelCode).filter((c) => c != null)});
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
    loadModelOptions({});
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
    stickyPolicy: normalizeStickyPolicy(form.stickyPolicy),
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

// 下拉缺失值守卫：已选项被删除/无权限时阻止提交（options 契约第 10 条）
const missing = useMissingOptionGuard();

async function submit() {
  if (missing.hasMissing.value) {
    ElMessage.warning(missing.hint.value);
    return;
  }
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

.section-head p {
  margin: 5px 0 0;
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

.sticky-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.sticky-header-input {
  width: 260px;
}

.sticky-hint {
  flex: 1 1 320px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
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


</style>
