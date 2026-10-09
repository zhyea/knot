<template>
  <el-drawer
    :model-value="modelValue"
    :title="isEdit ? '编辑路由规则' : '新建路由规则'"
    size="55%"
    class="drawer-with-scrollbar"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >
    <el-scrollbar max-height="calc(100vh - 140px)">
      <el-form :model="form" label-width="100px" class="routing-rule-form">
        <div class="slot-body rule-section">
          <div class="section-head">
            <div>
              <h3>基础信息</h3>
              <p>定义规则编码、名称、绑定应用与应用场景，编码用于接口与审计定位。</p>
            </div>
            <el-form-item label="启用" class="inline-switch">
              <el-switch v-model="form.enabled"/>
            </el-form-item>
          </div>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="规则编码" required :error="ruleCodeError">
                <el-input
                  v-model="form.ruleCode"
                  placeholder="最长 64 位"
                  maxlength="64"
                  show-word-limit
                  @blur="validateRuleCode"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="名称" required>
                <el-input v-model="form.name"/>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="绑定应用" required>
                <RemoteEntitySelect
                  v-model="form.appId"
                  :load-function="loadAppOptions"
                  :selected-options="selectedAppOptions"
                  placeholder="请选择应用"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="应用场景">
                <el-select
                  v-model="form.appScenarios"
                  placeholder="如：知识库问答、客服对话"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  clearable
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="绑定消费者" required class="bind-block-item consumer-bind-item">
                <RemoteEntitySelect
                  v-model="selectedConsumerId"
                  :load-function="loadConsumerOptions"
                  :selected-options="selectedConsumers"
                  placeholder="请选择消费者"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <div class="space-line"/>

        <div class="slot-body rule-section">
          <div class="section-head">
            <div>
              <h3>绑定路由目标</h3>
              <p>路由目标可以是供应商模型或模型池，模型池会按自身策略解析为最终模型。</p>
            </div>
          </div>
          <el-form-item label="目标类型" class="bind-block-item model-bind-item">
            <el-radio-group v-model="targetType">
              <el-radio-button
                v-for="item in targetTypeOptions"
                :key="item.value"
                :value="item.value"
              >{{ item.label }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="绑定目标" required class="bind-block-item model-bind-item">
            <RemoteEntitySelect
              :key="targetType"
              v-model="selectedTargetCodes"
              :load-function="loadTargetOptions"
              :extra-params="targetExtraParams"
              placeholder="请选择路由目标，可多选"
              :multiple="true"
              collapse-tags
              collapse-tags-tooltip
              style="width: 100%"
              @change="onSelectedTargetsChange"
            />
          </el-form-item>
          <el-table v-if="form.targets.length" :data="boundTargetRows" border :row-key="targetKey"
                    class="bind-table model-bind-table">
            <el-table-column label="目标类型" width="100" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="bind-list__text">{{ targetTypeLabel(row.targetType) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="targetCode" label="目标编码" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="bind-list__text">{{ row.targetCode || "—" }}</span>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="160" align="center">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.priority"
                  :min="0"
                  :max="9999"
                  :disabled="primaryTargetKey === targetKey(row)"
                  class="bind-table-number"
                />
              </template>
            </el-table-column>
            <el-table-column label="主目标" width="80" align="center">
              <template #default="{ row }">
                <el-radio v-model="primaryTargetKeyModel" :value="targetKey(row)" label=""/>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ row }">
                <el-button link type="danger" @click="removeTarget(row)">
                  <el-icon>
                    <Delete/>
                  </el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="space-line"/>

        <TrafficPolicySection
          class="slot-body rule-section"
          mode="rate"
          title="限流配置"
          description="按需覆盖路由规则级限流，留空时不单独配置。"
          v-model:rate-limit="form.rateLimitPolicy"
        />

        <div class="space-line"/>

        <RetryPolicySection
          class="slot-body rule-section"
          title="失败重试"
          description="上游瞬态失败时先在同一目标上原地重投，次数耗尽才切换到下一个候选。"
          v-model:retry-policy="form.retryPolicy"
        />
      </el-form>
    </el-scrollbar>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import {type PropType, computed, reactive, ref, watch, type Ref} from "vue";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {ElMessage} from "element-plus";
import {Delete} from "@element-plus/icons-vue";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import TrafficPolicySection from "../common/TrafficPolicySection.vue";
import RetryPolicySection from "../common/RetryPolicySection.vue";
import {
  emptyRateLimitPolicy,
  isEmptyRateLimitPolicy,
  normalizeRateLimitPolicy,
  type RateLimitPolicy
} from "@/utils/trafficPolicy";
import {defaultRetryPolicy, normalizeRetryPolicy, type RetryPolicy} from "@/utils/retryPolicy";
import {createRoutingRule, updateRoutingRule, checkRoutingRuleCode} from "@/api/routing";
import {listAppOptions, listModelOptions, listModelPoolOptions, listRoutingConsumerOptions} from "@/api/options";
import {toOptionsLoader} from "@/utils/options";
import type {Dict, Row} from "@/types";
import {useMissingOptionGuard} from "@/composables/useMissingOptionGuard";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  rule: {type: Object as PropType<Dict | null>, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const isEdit = computed(() => props.rule != null);

const appOptions = ref<Row[]>([]);
const modelOptions = ref<Row[]>([]);
const modelPoolOptions = ref<Row[]>([]);
const consumerOptions = ref<Row[]>([]);
const saving = ref(false);
const ruleCodeError = ref("");
const primaryTargetKey = ref<string | null>(null);
/** el-radio 的 v-model 不接受 null：null（未指定主目标）映射为 undefined */
const primaryTargetKeyModel = computed<string | undefined>({
  get: () => primaryTargetKey.value ?? undefined,
  set: (value) => {
    primaryTargetKey.value = value ?? null;
  }
});
const targetType = ref("MODEL");

// 路由目标类型：后端代码枚举 RouteTargetTypeEnum（/api/common/enums）
const {optionsOf: rtEnumOptionsOf, labelOf: enumLabelOf} = useEnumOptions();
const targetTypeOptions = computed(() => rtEnumOptionsOf("RouteTargetTypeEnum"));

interface RuleTargetForm {
  id?: number | string | null;
  targetType: string;
  targetCode: string | null;
  targetName: string | null;
  priority: number;
  primary: boolean;
}

interface RuleForm {
  id: number | string | null;
  ruleCode: string;
  name: string;
  appScenarios: string[];
  consumerIds: Array<string | number>;
  appId: number | string | null;
  enabled: boolean;
  targets: RuleTargetForm[];
  rateLimitPolicy: RateLimitPolicy;
  retryPolicy: RetryPolicy;
}

const form = reactive<RuleForm>({
  id: null,
  ruleCode: "",
  name: "",
  appScenarios: [],
  consumerIds: [],
  appId: null,
  enabled: true,
  targets: [],
  rateLimitPolicy: emptyRateLimitPolicy(),
  retryPolicy: defaultRetryPolicy()
});

const selectedConsumers = computed(() =>
  form.consumerIds.map((id, index) => {
    const consumer = consumerOptions.value.find((item) => String(item.value) === String(id));
    return consumer || {value: id, label: props.rule?.consumerNames?.[index] || `#${id}`};
  })
);
const selectedAppOptions = computed(() => {
  if (form.appId == null) {
    return [];
  }
  const app = appOptions.value.find((item) => String(item.value) === String(form.appId));
  return [app || {value: form.appId, label: props.rule?.appName || `#${form.appId}`}];
});
const selectedConsumerId = computed<string | number | null>({
  get: () => (form.consumerIds.length ? form.consumerIds[0] : null),
  set: (value) => {
    form.consumerIds = value == null ? [] : [value];
  }
});
const selectedTargetCodes = computed({
  get: () => form.targets.filter((item) => item.targetType === targetType.value).map((item) => item.targetCode).filter((c) => c != null),
  set: (codes) => onSelectedTargetsChange(codes)
});
const boundTargetRows = computed(() =>
  form.targets.map((item) => {
    const source = findTargetOption(item.targetType, item.targetCode);
    item.targetCode = item.targetCode || targetOptionCode(item.targetType, source);
    item.targetName = targetOptionName(source) || item.targetName;
    return item;
  })
);
const targetExtraParams = computed(() => ({status: "ENABLED"}));

const loadAppOptions = toOptionsLoader(listAppOptions, appOptions);

const loadConsumerOptions = toOptionsLoader(listRoutingConsumerOptions, consumerOptions);



const loadModelOptions = toOptionsLoader(listModelOptions, modelOptions);
const loadModelPoolOptions = toOptionsLoader(listModelPoolOptions, modelPoolOptions);
const loadTargetOptions = (params: Dict) =>
  (targetType.value === "MODEL_POOL" ? loadModelPoolOptions(params) : loadModelOptions(params));

function findTargetOption(type: string, code: unknown): Row | undefined {
  const options = type === "MODEL_POOL" ? modelPoolOptions.value : modelOptions.value;
  return options.find((item) => String(item.value) === String(code));
}

function targetOptionCode(type: string, option: Row | null | undefined): string {
  if (!option) return "";
  return String(option.value ?? "");
}

function targetOptionName(option: Row | null | undefined): string {
  if (!option) return "";
  return option.label || String(option.value ?? "");
}

function targetKey(row: Row): string {
  return `${row.targetType}:${row.targetCode}`;
}

function targetTypeLabel(type: string): string {
  return enumLabelOf("RouteTargetTypeEnum", type, type);
}

function parseAppScenarioTags(value: unknown): string[] {
  if (!value) {
    return [];
  }
  return String(value)
    .split(/[，,]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function buildAppScenarioValue() {
  const tags = (form.appScenarios || []).map((item) => String(item).trim()).filter(Boolean);
  return tags.length ? tags.join("，") : null;
}

function normalizeRuleCode(value: unknown): string {
  return String(value || "").trim().toLowerCase();
}

async function loadOptions() {
  // 预热候选：toOptionsLoader 已把结果并入各自累加器（appOptions/consumerOptions）
  await Promise.all([
    loadAppOptions({pageNum: 1, pageSize: 10}),
    loadConsumerOptions({pageNum: 1, pageSize: 10})
  ]);
}

function resetForm() {
  targetType.value = "MODEL";
  if (props.rule) {
    const row = props.rule;
    form.id = row.id;
    form.ruleCode = normalizeRuleCode(row.ruleCode);
    form.name = row.name || "";
    form.appScenarios = parseAppScenarioTags(row.appScenario);
    form.consumerIds = Array.isArray(row.consumerIds) ? [...row.consumerIds] : [];
    form.appId = row.appId ?? null;
    form.enabled = row.enabled !== false;
    form.rateLimitPolicy = normalizeRateLimitPolicy(row.rateLimitPolicy);
    form.retryPolicy = normalizeRetryPolicy(row.retryPolicy);
    form.targets = (row.targets || []).map((m: Dict) => ({
      targetType: m.targetType || "MODEL",
      targetCode: m.targetCode,
      targetName: m.targetName || m.name,
      priority: m.priority ?? 100,
      primary: !!m.primary
    }));
    const primaryTarget = form.targets.find((m) => m.primary) || form.targets[0] || null;
    primaryTargetKey.value = primaryTarget ? targetKey(primaryTarget) : null;
  } else {
    form.id = null;
    form.ruleCode = "";
    form.name = "";
    form.appScenarios = [];
    form.consumerIds = [];
    form.appId = null;
    form.enabled = false;
    form.targets = [];
    form.rateLimitPolicy = emptyRateLimitPolicy();
    form.retryPolicy = defaultRetryPolicy();
    primaryTargetKey.value = null;
  }
  ruleCodeError.value = "";
}

watch(
  () => [props.modelValue, props.rule],
  ([visible]) => {
    if (visible) {
      resetForm();
      loadOptions();
    }
  }
);

watch(
  () => form.ruleCode,
  () => {
    const normalized = normalizeRuleCode(form.ruleCode);
    if (form.ruleCode !== normalized) {
      form.ruleCode = normalized;
      return;
    }
    if (ruleCodeError.value) {
      ruleCodeError.value = "";
    }
  }
);

function onClosed() {
  form.id = null;
}

function onSelectedTargetsChange(targetCodes: Array<string | number | null>) {
  const nextCodes = Array.isArray(targetCodes) ? targetCodes : [];
  const existingByKey = new Map(form.targets.map((item) => [targetKey(item), item]));
  const otherTargets = form.targets.filter((item) => item.targetType !== targetType.value);
  const previousPrimaryKey = primaryTargetKey.value;
  const currentTargets = nextCodes.map((targetCode) => {
    const key = `${targetType.value}:${targetCode}`;
    const source = findTargetOption(targetType.value, targetCode);
    return existingByKey.get(key) || {
      targetType: targetType.value,
      targetCode: String(targetCode),
      targetName: targetOptionName(source),
      priority: 100,
      primary: false
    };
  });
  form.targets = [...otherTargets, ...currentTargets];
  if (!previousPrimaryKey && currentTargets.length) {
    primaryTargetKey.value = targetKey(currentTargets[0]);
    return;
  }
  if (!form.targets.some((item) => targetKey(item) === previousPrimaryKey)) {
    primaryTargetKey.value = currentTargets[0]
      ? targetKey(currentTargets[0])
      : (form.targets[0] ? targetKey(form.targets[0]) : null);
  }
}

function removeTarget(row: Row) {
  form.targets = form.targets.filter((item) => targetKey(item) !== targetKey(row));
  if (primaryTargetKey.value === targetKey(row)) {
    primaryTargetKey.value = form.targets[0] ? targetKey(form.targets[0]) : null;
  }
}

function resolvePrimaryTargetKey() {
  if (!form.targets.length) {
    return null;
  }
  if (primaryTargetKey.value && form.targets.some((item) => targetKey(item) === primaryTargetKey.value)) {
    return primaryTargetKey.value;
  }
  return targetKey(form.targets[0]);
}

async function validateRuleCode() {
  const code = normalizeRuleCode(form.ruleCode);
  form.ruleCode = code;
  if (!code) {
    ruleCodeError.value = "请填写规则编码";
    return false;
  }
  try {
    const res = await checkRoutingRuleCode(code, isEdit.value ? form.id : null);
    if (res?.available) {
      ruleCodeError.value = "";
      return true;
    }
    ruleCodeError.value = "规则编码已存在，请更换";
    return false;
  } catch {
    return false;
  }
}

function buildSubmitPayload() {
  const primaryKey = resolvePrimaryTargetKey();
  primaryTargetKey.value = primaryKey;
  const targets = form.targets.map((m) => ({
    targetType: m.targetType,
    targetCode: m.targetCode,
    priority: m.priority ?? 100,
    primary: targetKey(m) === primaryKey
  }));
  const rateLimitPolicy = isEmptyRateLimitPolicy(form.rateLimitPolicy)
    ? null
    : normalizeRateLimitPolicy(form.rateLimitPolicy);
  return {
    ruleCode: normalizeRuleCode(form.ruleCode),
    name: form.name?.trim(),
    appScenario: buildAppScenarioValue(),
    consumerIds: [...form.consumerIds],
    appId: form.appId,
    enabled: form.enabled,
    targets,
    rateLimitPolicy,
    // 恒提交归一化结果：关闭重试也要显式落库（enabled=false），
    // 若传 null 会被运行时当成「未配置」而退回默认开启策略
    retryPolicy: normalizeRetryPolicy(form.retryPolicy)
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
  if (!(await validateRuleCode())) {
    return;
  }
  if (form.enabled) {
    if (!form.consumerIds.length) {
      ElMessage.warning("启用规则前请选择消费者");
      return;
    }
    if (!form.appId) {
      ElMessage.warning("启用规则前请选择绑定应用");
      return;
    }
    if (!form.targets.length || form.targets.some((m) => !m.targetCode)) {
      ElMessage.warning("启用规则前请完整配置路由目标");
      return;
    }
    const targetKeys = form.targets.map((m) => targetKey(m));
    if (new Set(targetKeys).size !== targetKeys.length) {
      ElMessage.warning("路由目标不能重复");
      return;
    }
    if (!resolvePrimaryTargetKey()) {
      ElMessage.warning("启用规则前请指定主目标");
      return;
    }
  }
  saving.value = true;
  try {
    const body = buildSubmitPayload();
    if (isEdit.value) {
      await updateRoutingRule(form.id!, body);
      ElMessage.success("已保存");
    } else {
      await createRoutingRule(body);
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
.routing-rule-form {
  padding-bottom: 8px;
}

.rule-section {
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

.bind-table {
  margin-top: 10px;
  width: 100%;
}

.bind-table :deep(.el-table__cell) {
  font-size: 12px;
}

.bind-table :deep(th.el-table__cell) {
  font-size: 12px;
  font-weight: 600;
}

.bind-table :deep(.cell) {
  padding-left: 12px;
  padding-right: 12px;
}

.bind-table-number {
  width: 118px;
}

.bind-table-number :deep(.el-input__inner) {
  font-size: 12px;
}

.bind-table :deep(.el-input-number),
.bind-table :deep(.el-radio),
.bind-table :deep(.el-button) {
  font-size: 12px;
}

.bind-list__text {
  overflow: hidden;
  color: #303133;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .section-head {
    display: block;
  }

  .inline-switch {
    margin-top: 12px;
  }
}
</style>
