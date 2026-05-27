<template>
  <el-drawer
      :model-value="modelValue"
      :title="isEdit ? '编辑路由规则' : '新建路由规则'"
      size="55%"
      destroy-on-close
      @update:model-value="emit('update:modelValue', $event)"
      @closed="onClosed"
  >
    <el-form :model="form" label-width="100px" class="routing-rule-form">
      <div class="slot-body rule-section">
        <div class="section-head">
          <div>
            <h3>基础信息</h3>
            <p>定义规则编码、名称和可选应用场景，编码用于接口与审计定位。</p>
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
                  placeholder="最长 32 位"
                  maxlength="32"
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
          <el-col :span="24">
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
      </div>

      <div class="space-line"/>

      <div class="slot-body rule-section">
        <div class="section-head">
          <div>
            <h3>路由配置</h3>
            <p>指定当前规则关联的应用、用户和路由策略。</p>
          </div>
        </div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="绑定应用" required>
              <el-select v-model="form.appId" placeholder="请选择应用" filterable style="width: 100%">
                <el-option v-for="app in appOptions" :key="app.id" :label="app.name" :value="app.id"/>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用户">
              <el-select
                  v-model="form.userId"
                  placeholder="请选择用户"
                  clearable
                  filterable
                  style="width: 100%"
              >
                <el-option
                    v-for="user in userOptions"
                    :key="user.id"
                    :label="userLabel(user)"
                    :value="user.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="策略类型">
              <EnumSelect v-model="form.strategy" category="strategy_type" show-code/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型类型" required>
              <EnumSelect
                  v-model="form.modelTypes"
                  category="model_type"
                  multiple
              />
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <div class="space-line"/>

      <div class="slot-body rule-section">
        <div class="section-head">
          <div>
            <h3>绑定消费者</h3>
            <p>消费者维护 API Key，一个消费者可以对应多个路由规则。</p>
          </div>
        </div>
        <el-form-item label="绑定消费者" required class="bind-block-item consumer-bind-item">
          <el-select
              v-model="form.consumerIds"
              placeholder="请选择消费者，可多选"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              style="width: 100%"
              @change="onConsumersChange"
          >
            <el-option
                v-for="consumer in consumerOptions"
                :key="consumer.id"
                :label="consumerLabel(consumer)"
                :value="consumer.id"
            />
          </el-select>
        </el-form-item>
        <div v-if="selectedConsumers.length" class="bind-list consumer-bind-list">
          <div class="bind-list__row bind-list__header consumer-bind-list__row">
            <span>消费者编码</span>
            <span>消费者名称</span>
            <span>是否启用</span>
          </div>
          <div
              v-for="consumer in selectedConsumers"
              :key="consumer.id"
              class="bind-list__row consumer-bind-list__row"
          >
            <span class="bind-list__text">{{ consumer.consumerCode || "—" }}</span>
            <span class="bind-list__text">{{ consumer.name || "—" }}</span>
            <span>
              <el-tag size="small" :type="consumer.enabled === false ? 'info' : 'success'">
                {{ consumer.enabled === false ? "停用" : "启用" }}
              </el-tag>
            </span>
          </div>
        </div>
      </div>

      <div class="space-line"/>

      <div class="slot-body rule-section">
        <div class="section-head">
          <div>
            <h3>绑定模型</h3>
            <p>配置候选模型和优先级，并指定唯一主模型。</p>
          </div>
        </div>
        <el-form-item label="绑定模型" required class="bind-block-item model-bind-item">
          <el-select
              v-model="selectedModelIds"
              placeholder="请选择模型，可多选"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              style="width: 100%"
              @change="onSelectedModelsChange"
          >
            <el-option
                v-for="m in filteredModelOptions"
                :key="m.id"
                :label="`${m.name}（${m.modelCode}）`"
                :value="m.id"
            />
          </el-select>
        </el-form-item>
        <div v-if="form.models.length" class="bind-list model-bind-list">
          <div class="bind-list__row bind-list__header model-bind-list__row">
            <span>模型编码</span>
            <span>模型名称</span>
            <span>优先级</span>
            <span>主模型</span>
            <span>操作</span>
          </div>
          <div
              v-for="row in boundModelRows"
              :key="row.modelId"
              class="bind-list__row model-bind-list__row"
          >
            <span class="bind-list__text">{{ row.modelCode || "—" }}</span>
            <span class="bind-list__text">{{ row.name || "—" }}</span>
            <span>
              <el-input-number v-model="row.priority" :min="0" :max="9999" />
            </span>
            <span>
              <el-radio v-model="primaryModelId" :value="row.modelId" label="" />
            </span>
            <span>
              <el-button link type="danger" @click="removeModel(row.modelId)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </span>
          </div>
        </div>
      </div>

      <div class="space-line"/>

      <div class="slot-body rule-section">
        <div class="section-head">
          <div>
            <h3>策略配置</h3>
            <p>按需覆盖频控和额度策略，留空时不单独配置。</p>
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
import {Delete} from "@element-plus/icons-vue";
import KvEditor from "../common/KvEditor.vue";
import EnumSelect from "../common/EnumSelect.vue";
import {
  emptyQuotaPolicy,
  emptyRateLimitPolicy,
  isEmptyQuotaPolicy,
  isEmptyRateLimitPolicy,
  normalizeQuotaPolicy,
  normalizeRateLimitPolicy
} from "../../utils/trafficPolicy";
import {createRoutingRule, updateRoutingRule, checkRoutingRuleCode, listRoutingConsumers} from "../../api/routing";
import {generateRoutingRuleCode} from "../../utils/routingRule";
import {listApps} from "../../api/apps";
import {listModels} from "../../api/models";
import {listUsers} from "../../api/users";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  rule: {type: Object, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const isEdit = computed(() => props.rule != null);

const appOptions = ref([]);
const userOptions = ref([]);
const modelOptions = ref([]);
const consumerOptions = ref([]);
const saving = ref(false);
const ruleCodeError = ref("");
const primaryModelId = ref(null);

const form = reactive({
  id: null,
  ruleCode: "",
  name: "",
  appScenarios: [],
  consumerIds: [],
  appId: null,
  userId: null,
  modelTypes: ["CHAT"],
  strategy: "PRIORITY",
  enabled: true,
  models: [],
  rateLimitPolicy: emptyRateLimitPolicy(),
  quotaPolicy: emptyQuotaPolicy()
});

const selectedConsumers = computed(() =>
    consumerOptions.value.filter((item) => form.consumerIds.includes(item.id))
);
const filteredModelOptions = computed(() => {
  const selectedTypes = new Set((form.modelTypes?.length ? form.modelTypes : ["CHAT"]));
  return modelOptions.value.filter((item) => selectedTypes.has(item.modelType));
});
const selectedModelIds = computed({
  get: () => form.models.map((item) => item.modelId).filter((id) => id != null),
  set: (ids) => onSelectedModelsChange(ids)
});
const boundModelRows = computed(() =>
    form.models.map((item) => {
      const model = modelOptions.value.find((m) => m.id === item.modelId);
      item.modelCode = model?.modelCode;
      item.name = model?.name;
      return item;
    })
);

function userLabel(user) {
  const name = user.realName?.trim() || user.username;
  return name === user.username ? name : `${name}（${user.username}）`;
}

function consumerLabel(consumer) {
  return consumer.name || consumer.consumerCode;
}

function parseAppScenarioTags(value) {
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

async function loadOptions() {
  const [appsRes, usersRes, modelsRes, consumersRes] = await Promise.all([
    listApps({pageNum: 1, pageSize: 500}),
    listUsers({pageNum: 1, pageSize: 500}),
    listModels({pageNum: 1, pageSize: 500}),
    listRoutingConsumers({pageNum: 1, pageSize: 500})
  ]);
  appOptions.value = Array.isArray(appsRes?.list) ? appsRes.list : [];
  userOptions.value = Array.isArray(usersRes?.list) ? usersRes.list : Array.isArray(usersRes) ? usersRes : [];
  modelOptions.value = Array.isArray(modelsRes?.list) ? modelsRes.list : [];
  consumerOptions.value = Array.isArray(consumersRes?.list) ? consumersRes.list : [];
}

function resetForm() {
  if (props.rule) {
    const row = props.rule;
    form.id = row.id;
    form.ruleCode = row.ruleCode || "";
    form.name = row.name || "";
    form.appScenarios = parseAppScenarioTags(row.appScenario);
    form.consumerIds = Array.isArray(row.consumerIds) ? [...row.consumerIds] : [];
    form.appId = row.appId ?? null;
    form.userId = row.userId ?? null;
    form.modelTypes = Array.isArray(row.modelTypes) && row.modelTypes.length ? [...row.modelTypes] : ["CHAT"];
    form.strategy = row.strategy || "PRIORITY";
    form.enabled = row.enabled !== false;
    form.rateLimitPolicy = normalizeRateLimitPolicy(row.rateLimitPolicy);
    form.quotaPolicy = normalizeQuotaPolicy(row.quotaPolicy);
    form.models = (row.models || []).map((m) => ({
      modelId: m.modelId,
      priority: m.priority ?? 100,
      primary: !!m.primary
    }));
    primaryModelId.value = form.models.find((m) => m.primary)?.modelId ?? form.models[0]?.modelId ?? null;
  } else {
    form.id = null;
    form.ruleCode = generateRoutingRuleCode();
    form.name = "";
    form.appScenarios = [];
    form.consumerIds = [];
    form.appId = null;
    form.userId = null;
    form.modelTypes = ["CHAT"];
    form.strategy = "PRIORITY";
    form.enabled = false;
    form.models = [];
    form.rateLimitPolicy = emptyRateLimitPolicy();
    form.quotaPolicy = emptyQuotaPolicy();
    primaryModelId.value = null;
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
      if (ruleCodeError.value) {
        ruleCodeError.value = "";
      }
    }
);

watch(
    () => [...(form.modelTypes || [])],
    () => {
      const allowedModelIds = new Set(filteredModelOptions.value.map((item) => item.id));
      let changed = false;
      form.models = form.models.map((item) => {
        if (item.modelId && !allowedModelIds.has(item.modelId)) {
          changed = true;
          return null;
        }
        return item;
      }).filter(Boolean);
      if (changed) {
        primaryModelId.value = form.models[0]?.modelId ?? null;
      }
    }
);

function onClosed() {
  form.id = null;
}

function onConsumersChange(consumerIds) {
  const consumer = consumerOptions.value.find((item) => consumerIds.includes(item.id));
  if (!form.userId && consumer?.userId) {
    form.userId = consumer.userId;
  }
}

function onSelectedModelsChange(modelIds) {
  const nextIds = Array.isArray(modelIds) ? modelIds : [];
  const existingById = new Map(form.models.map((item) => [item.modelId, item]));
  form.models = nextIds.map((modelId) => existingById.get(modelId) || {modelId, priority: 100, primary: false});
  if (!nextIds.includes(primaryModelId.value)) {
    primaryModelId.value = nextIds[0] ?? null;
  }
}

function removeModel(modelId) {
  onSelectedModelsChange(form.models.map((item) => item.modelId).filter((id) => id !== modelId));
}

async function validateRuleCode() {
  const code = form.ruleCode?.trim();
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
  const models = form.models.map((m, idx) => ({
    modelId: m.modelId,
    priority: m.priority ?? 100,
    primary: m.modelId === primaryModelId.value
  }));
  const rateLimitPolicy = isEmptyRateLimitPolicy(form.rateLimitPolicy)
      ? null
      : normalizeRateLimitPolicy(form.rateLimitPolicy);
  const quotaPolicy = isEmptyQuotaPolicy(form.quotaPolicy) ? null : normalizeQuotaPolicy(form.quotaPolicy);
  return {
    ruleCode: form.ruleCode?.trim(),
    name: form.name?.trim(),
    appScenario: buildAppScenarioValue(),
    consumerIds: [...form.consumerIds],
    appId: form.appId,
    userId: form.userId,
    modelTypes: form.modelTypes?.length ? [...form.modelTypes] : ["CHAT"],
    strategy: form.strategy,
    enabled: form.enabled,
    models,
    rateLimitPolicy,
    quotaPolicy
  };
}

async function submit() {
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
    if (!form.modelTypes?.length) {
      ElMessage.warning("启用规则前请选择模型类型");
      return;
    }
    if (!form.models.length || form.models.some((m) => !m.modelId)) {
      ElMessage.warning("启用规则前请完整配置绑定模型");
      return;
    }
    const modelIds = form.models.map((m) => m.modelId);
    if (new Set(modelIds).size !== modelIds.length) {
      ElMessage.warning("模型绑定不能重复");
      return;
    }
    if (!primaryModelId.value) {
      ElMessage.warning("启用规则前请指定主模型");
      return;
    }
  }
  saving.value = true;
  try {
    const body = buildSubmitPayload();
    if (isEdit.value) {
      await updateRoutingRule(form.id, body);
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
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.consumer-bind-list__row {
  grid-template-columns: minmax(160px, 1fr) minmax(160px, 1fr) 100px;
}

.model-bind-list__row {
  grid-template-columns: minmax(150px, 1fr) minmax(180px, 1.2fr) 160px 80px 70px;
}

.model-bind-list__row > span:nth-child(4),
.model-bind-list__row > span:nth-child(5),
.consumer-bind-list__row > span:nth-child(3) {
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

  .consumer-bind-list__row,
  .model-bind-list__row {
    grid-template-columns: 1fr;
    row-gap: 8px;
  }

  .model-bind-list__row > span:nth-child(4),
  .model-bind-list__row > span:nth-child(5),
  .consumer-bind-list__row > span:nth-child(3) {
    justify-content: flex-start;
  }
}
</style>
