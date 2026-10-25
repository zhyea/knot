<template>
  <el-drawer
    :model-value="modelValue"
    :title="isEdit ? '编辑路由规则' : '新建路由规则'"
    size="64%"
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
            <el-col :span="24">
            <el-form-item label="规则编码" required :error="ruleCodeError">
              <el-input
                v-model="form.ruleCode"
                placeholder="最长 64 位"
                maxlength="64"
                show-word-limit
                :readonly="codeLocked"
                @blur="validateRuleCode"
              />
              <div v-if="codeLocked" class="form-tip">{{ codeLockHint }}</div>
            </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="名称" required>
                <el-input v-model="form.name"/>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="统一模型" >
                <RemoteEntitySelect
                  v-model="form.logicalModelCode"
                  :load-function="loadLogicalModelOptions"
                  :selected-options="selectedLogicalModelOptions"
                  placeholder="可选：选择后，下方目标列表优先展示该统一模型对应的模型/模型池"
                  clearable
                  style="width: 100%"
                />
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
              <el-form-item label="消费者" required>
                <RemoteEntitySelect
                  v-model="selectedConsumerId"
                  :load-function="loadConsumerOptions"
                  :selected-options="selectedConsumers"
                  placeholder="请选择消费者"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="归属用户">
                <RemoteEntitySelect
                  v-model="form.username"
                  :load-function="loadUserUsernameOptions"
                  :selected-options="selectedUsernameOptions"
                  placeholder="请选择归属用户"
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
          <el-row :gutter="16">
            <el-col :span="12">
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
            </el-col>
            <el-col :span="12">
              <el-form-item label="目标选择策略" class="bind-block-item model-bind-item">
                <el-select v-model="form.selectionStrategy" placeholder="请选择目标选择策略" style="width: 100%">
                  <el-option
                    v-for="item in selectionStrategyOptions"
                    :key="String(item.value)"
                    :label="item.label"
                    :value="String(item.value)"
                  />
                </el-select>
                <div class="field-tip">
                  优先级：按下方目标的主目标与优先级固定排序；最少负载：按各目标当前在途并发动态排序（最空闲优先，主目标仍排首位）。
                </div>
              </el-form-item>
            </el-col>
          </el-row>
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
              :max-collapse-tags="3"
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

        <div class="space-line"/>

        <EjectionPolicySection
          class="slot-body rule-section"
          title="异常摘除"
          description="窗口内反复异常的供应商模型会被临时移出候选链，冷却结束后由探活请求恢复。"
          v-model:ejection-policy="form.ejectionPolicy"
        />

        <div v-if="isEdit" class="space-line"/>

        <!-- 当前摘除目标（B4 管理面）：跨抽象层的网关运行时健康态，独立成组件 -->
        <RoutingRuleOutagePanel
          v-if="isEdit"
          ref="outagePanelRef"
          :rule-code="form.ruleCode"
          :model-codes="targetModelCodes"
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
import {type PropType, computed, nextTick, reactive, ref, watch} from "vue";
import {RouteTargetType} from "@/constants/status";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {ElMessage} from "element-plus";
import {Delete} from "@element-plus/icons-vue";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import RoutingRuleOutagePanel from "./RoutingRuleOutagePanel.vue";
import TrafficPolicySection from "../common/TrafficPolicySection.vue";
import RetryPolicySection from "../common/RetryPolicySection.vue";
import EjectionPolicySection from "../common/EjectionPolicySection.vue";
import {
  emptyRateLimitPolicy,
  isEmptyRateLimitPolicy,
  normalizeRateLimitPolicy,
  type RateLimitPolicy
} from "@/utils/trafficPolicy";
import {defaultRetryPolicy, normalizeRetryPolicy, type RetryPolicy} from "@/utils/retryPolicy";
import {
  defaultEjectionPolicy,
  normalizeEjectionPolicy,
  type EjectionPolicy
} from "@/utils/ejectionPolicy";
import {createRoutingRule, updateRoutingRule, checkRoutingRuleCode} from "@/api/routing";
import {listAppOptions, listLogicalModelOptions, listModelOptions, listModelPoolOptions, listRoutingConsumerOptions, listUserUsernameOptions} from "@/api/options";
import {toOptionsLoader} from "@/utils/options";
import type {Dict, Row} from "@/types";
import {useMissingOptionGuard} from "@/composables/useMissingOptionGuard";
import {useCodeBindingLock} from "@/composables/useCodeBindingLock";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  rule: {type: Object as PropType<Dict | null>, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const isEdit = computed(() => props.rule != null);

/**
 * 规则编码口径同其它业务码：编辑态且已被引用（有路由目标或限流绑定）才锁定。
 * boundCount 由后端在 DTO/VO 上派生（目标行数 + 限流绑定行）。
 */
const {codeLocked, codeLockHint} = useCodeBindingLock(() => isEdit.value, () => props.rule?.boundCount);

const appOptions = ref<Row[]>([]);
const logicalModelOptions = ref<Row[]>([]);
const modelOptions = ref<Row[]>([]);
const modelPoolOptions = ref<Row[]>([]);
const consumerOptions = ref<Row[]>([]);
const userUsernameOptions = ref<Row[]>([]);
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
const targetType = ref<string>(RouteTargetType.MODEL);

// 路由目标类型：后端代码枚举 RouteTargetTypeEnum（/api/enum-options）
const {optionsOf: rtEnumOptionsOf, labelOf: enumLabelOf} = useEnumOptions();
const targetTypeOptions = computed(() => rtEnumOptionsOf("RouteTargetTypeEnum"));
/** 目标选择策略：复用池级枚举，但规则层只开放已实现的「优先级 / 最少负载」两种 */
const selectionStrategyOptions = computed(() =>
  rtEnumOptionsOf("ModelPoolSelectionStrategyEnum", ["PRIORITY", "LEAST_LOAD"])
);

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
  /** 绑定消费者业务码（kb_routing_consumers.consumer_code），一对一，非主键 id */
  consumerCode: string | null;
  appId: number | string | null;
  /** 归属用户登录名（绑定 username，非 user.id） */
  username: string | null;
  /** 统一模型 code（kb_logical_models.model_code），非必填；选中后目标选择器优先置前对应项（仅排序，不过滤） */
  logicalModelCode: string | null;
  /** 目标选择策略（ModelPoolSelectionStrategyEnum）：PRIORITY 固定序 / LEAST_LOAD 按在途并发动态重排 */
  selectionStrategy: string;
  enabled: boolean;
  targets: RuleTargetForm[];
  rateLimitPolicy: RateLimitPolicy;
  retryPolicy: RetryPolicy;
  ejectionPolicy: EjectionPolicy;
}

const form = reactive<RuleForm>({
  id: null,
  ruleCode: "",
  name: "",
  appScenarios: [],
  consumerCode: null,
  appId: null,
  username: null,
  logicalModelCode: null,
  selectionStrategy: "PRIORITY",
  enabled: true,
  targets: [],
  rateLimitPolicy: emptyRateLimitPolicy(),
  retryPolicy: defaultRetryPolicy(),
  ejectionPolicy: defaultEjectionPolicy()
});

const selectedConsumers = computed<Row[]>(() => {
  if (!form.consumerCode) {
    return [];
  }
  const consumer = consumerOptions.value.find((item) => String(item.value) === String(form.consumerCode));
  return [consumer || {value: form.consumerCode, label: props.rule?.consumerName || `#${form.consumerCode}`}];
});
const selectedAppOptions = computed(() => {
  if (form.appId == null) {
    return [];
  }
  const app = appOptions.value.find((item) => String(item.value) === String(form.appId));
  return [app || {value: form.appId, label: props.rule?.appName || `#${form.appId}`}];
});
/** 统一模型下拉回显项：value=logicalModelCode；后端已派生 logicalModelName，优先用作 label */
const selectedLogicalModelOptions = computed<Row[]>(() => {
  if (!form.logicalModelCode) {
    return [];
  }
  const logical = logicalModelOptions.value.find((item) => String(item.value) === String(form.logicalModelCode));
  const fallbackLabel = props.rule?.logicalModelName || form.logicalModelCode;
  return [logical || {value: form.logicalModelCode, label: fallbackLabel}];
});
const selectedConsumerId = computed<string | number | null>({
  get: () => form.consumerCode ?? null,
  set: (value) => {
    form.consumerCode = value == null ? null : String(value);
  }
});

/** 归属用户下拉回显项：value 取 username（业务码）；label 优先「姓名(用户名)」，无真实姓名时回退纯 username。 */
const selectedUserRealName = ref<string | null>(null);
const selectedUsernameOptions = computed<Row[]>(() => {
  if (!form.username) {
    return [];
  }
  const realName = selectedUserRealName.value;
  const label = realName ? `${realName}(${form.username})` : form.username;
  return [{value: form.username, label}];
});
const selectedTargetCodes = computed({
  get: () => form.targets.filter((item) => item.targetType === targetType.value).map((item) => item.targetCode).filter((c) => c != null),
  set: (codes) => onSelectedTargetsChange(codes)
});
const boundTargetRows = computed(() =>
  form.targets.map((item) => {
    const source = findTargetOption(item.targetType, item.targetCode);
    item.targetCode = item.targetCode || targetOptionCode(source);
    item.targetName = targetOptionName(source) || item.targetName;
    return item;
  })
);
/** 目标下拉附带统一模型优先排序上下文：选中统一模型后对应项置前，但不过滤（其余项仍可出现）。 */
const targetExtraParams = computed(() => ({
  status: "ENABLED",
  preferredLogicalModelCode: form.logicalModelCode || undefined
}));

const loadAppOptions = toOptionsLoader(listAppOptions, appOptions);

const loadLogicalModelOptions = toOptionsLoader(listLogicalModelOptions, logicalModelOptions);

const loadConsumerOptions = toOptionsLoader(listRoutingConsumerOptions, consumerOptions);

const loadUserUsernameOptions = toOptionsLoader(listUserUsernameOptions, userUsernameOptions);



const loadModelOptions = toOptionsLoader(listModelOptions, modelOptions);
const loadModelPoolOptions = toOptionsLoader(listModelPoolOptions, modelPoolOptions);
const loadTargetOptions = (params: Dict) =>
  (targetType.value === RouteTargetType.MODEL_POOL ? loadModelPoolOptions(params) : loadModelOptions(params));

function findTargetOption(type: string, code: unknown): Row | undefined {
  const options = type === RouteTargetType.MODEL_POOL ? modelPoolOptions.value : modelOptions.value;
  return options.find((item) => String(item.value) === String(code));
}

function targetOptionCode(option: Row | null | undefined): string {
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

/** 规则编码归一：只去空白，**不改大小写**——与后端 `RoutingRuleService.normalizeRuleCode`
 *  （`ruleCode.trim()`）保持同一口径。前端若单方面转小写，会把 API 直写的大写编码在「打开抽屉即改写」，
 *  保存时静默改码；唯一性由 DB 的 `uk_routing_rules_code`（utf8mb4_unicode_ci）保证大小写不敏感。 */
function normalizeRuleCode(value: unknown): string {
  return String(value || "").trim();
}

async function loadOptions() {
  // 预热候选：toOptionsLoader 已把结果并入各自累加器（appOptions/consumerOptions）
  await Promise.all([
    loadAppOptions({pageNum: 1, pageSize: 10}),
    loadConsumerOptions({pageNum: 1, pageSize: 10})
  ]);
}

function resetForm() {
  targetType.value = RouteTargetType.MODEL;
  if (props.rule) {
    const row = props.rule;
    form.id = row.id;
    form.ruleCode = normalizeRuleCode(row.ruleCode);
    form.name = row.name || "";
    form.appScenarios = parseAppScenarioTags(row.appScenario);
    form.consumerCode = row.consumerCode || null;
    form.appId = row.appId ?? null;
    form.username = row.username || null;
    form.logicalModelCode = row.logicalModelCode ?? null;
    form.selectionStrategy = row.selectionStrategy || "PRIORITY";
    selectedUserRealName.value = row.userRealName ?? null;
    form.enabled = row.enabled !== false;
    form.rateLimitPolicy = normalizeRateLimitPolicy(row.rateLimitPolicy);
    form.retryPolicy = normalizeRetryPolicy(row.retryPolicy);
    form.ejectionPolicy = normalizeEjectionPolicy(row.ejectionPolicy);
    form.targets = (row.targets || []).map((m: Dict) => ({
      targetType: m.targetType || RouteTargetType.MODEL,
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
    form.consumerCode = null;
    form.appId = null;
    form.username = null;
    form.logicalModelCode = null;
    form.selectionStrategy = "PRIORITY";
    selectedUserRealName.value = null;
    form.enabled = false;
    form.targets = [];
    form.rateLimitPolicy = emptyRateLimitPolicy();
    form.retryPolicy = defaultRetryPolicy();
    form.ejectionPolicy = defaultEjectionPolicy();
    primaryTargetKey.value = null;
  }
  ruleCodeError.value = "";
}

/** 摘除面板句柄：刷新时机由本组件决定（打开/换规则），面板只负责自身状态机。 */
const outagePanelRef = ref<{ refresh: () => Promise<void> } | null>(null);

watch(
  () => [props.modelValue, props.rule],
  async ([visible]) => {
    if (visible) {
      resetForm();
      loadOptions();
      // 等本次渲染落地、面板 ref 就位后再拉取（首次打开时面板是刚挂载的）
      await nextTick();
      outagePanelRef.value?.refresh();
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
  // 主目标回落：`previousPrimaryKey == null`（首次选择）时下面的条件恒真，
  // 同一分支已覆盖「自动把首个设为主目标」，无需再单独早返回。
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
  // 编码被引用后锁定（后端同口径 409 拦截），锁定态无需再校验唯一性
  if (codeLocked.value) {
    ruleCodeError.value = "";
    return true;
  }
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
    consumerCode: form.consumerCode || null,
    appId: form.appId,
    username: form.username || null,
    logicalModelCode: form.logicalModelCode || null,
    selectionStrategy: form.selectionStrategy || "PRIORITY",
    enabled: form.enabled,
    targets,
    rateLimitPolicy,
    // 恒提交归一化结果：关闭重试也要显式落库（enabled=false），
    // 若传 null 会被运行时当成「未配置」而退回默认开启策略
    retryPolicy: normalizeRetryPolicy(form.retryPolicy),
    // 同上：摘除默认关闭，必须显式落库才能覆盖「未配置走默认」的语义
    ejectionPolicy: normalizeEjectionPolicy(form.ejectionPolicy)
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
    if (!form.consumerCode) {
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

/** 本规则绑定的「模型」类目标 code 列表，作为手动摘除候选（传给摘除面板，模型池不作为摘除对象）。 */
const targetModelCodes = computed(() =>
  form.targets
    .filter((t) => t.targetType === RouteTargetType.MODEL && t.targetCode)
    .map((t) => String(t.targetCode))
);
</script>

<style scoped>
.routing-rule-form {
  padding-bottom: 8px;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
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

.field-tip {
  width: 100%;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
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
