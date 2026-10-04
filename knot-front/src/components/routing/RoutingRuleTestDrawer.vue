<template>
  <el-drawer
    :model-value="modelValue"
    :title="drawerTitle"
    size="960px"
    class="drawer-with-scrollbar routing-test-drawer"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >
    <el-scrollbar max-height="calc(100vh - 140px)">
      <div class="slot-body routing-test">
        <section class="debug-panel debug-panel--request">
          <div class="request-toolbar">
            <div class="request-toolbar__main">
              <span class="request-toolbar__method">POST</span>
              <div class="request-toolbar__url">{{ requestUrl }}</div>
            </div>
            <el-button v-if="!isStreaming" type="primary" :loading="loading" :disabled="!ruleId" @click="runTest">
              发送请求
            </el-button>
            <el-button v-else type="danger" @click="stopStreaming">
              停止请求
            </el-button>
          </div>

          <div class="request-summary">
            <el-tag size="small" effect="plain">规则：{{ props.ruleName?.trim() || `#${props.ruleId || "-"}` }}</el-tag>
          </div>

          <el-form label-width="72px" class="test-form">
            <div class="test-form__row">
              <el-form-item label="协议">
                <el-select
                  v-model="testForm.protocol"
                  :loading="protocolLoading"
                  :disabled="availableProtocols.length === 0"
                  placeholder="选择接口协议"
                  style="width: 100%"
                  @change="ensureTemplateForCurrentSelection"
                >
                  <el-option
                    v-for="protocol in availableProtocols"
                    :key="protocol.code"
                    :label="protocol.label"
                    :value="protocol.code"
                  />
                </el-select>
              </el-form-item>
            </div>

            <el-collapse v-model="expandedPanels" class="debug-collapse">
              <el-collapse-item name="preview" title="请求预览">
                <el-tabs v-model="requestTab" class="debug-tabs debug-tabs--preview">
                  <el-tab-pane label="预设请求" name="preset">
                    <div class="preset-row">
                      <el-select
                        v-model="selectedPresetId"
                        :disabled="filteredPresetOptions.length === 0"
                        placeholder="从预设请求载入请求模板"
                        clearable
                        filterable
                        class="preset-select"
                        @change="onPresetChange"
                      >
                        <el-option
                          v-for="preset in filteredPresetOptions"
                          :key="preset.id"
                          :label="preset.name"
                          :value="preset.id"
                        />
                      </el-select>
                      <el-button size="small" @click="resetCurrentTemplate">重置模板</el-button>
                    </div>

                    <div class="request-template">
                      <JsonCodeEditor
                        v-model="currentTemplateText"
                        min-height="220px"
                        max-height="320px"
                      />
                      <div v-if="protocolHint" class="request-template__hint">{{ protocolHint }}</div>
                    </div>
                  </el-tab-pane>
                  <el-tab-pane label="Headers" name="headers">
                    <JsonCodeEditor :model-value="requestHeadersText" readonly min-height="220px" max-height="320px"/>
                  </el-tab-pane>
                  <el-tab-pane label="curl" name="curl">
                    <ShellCodeBlock :code="displayCurl" language="bash" :copyable="true"/>
                  </el-tab-pane>
                </el-tabs>
              </el-collapse-item>
            </el-collapse>
          </el-form>
        </section>

        <section class="debug-panel debug-panel--response">
          <div class="debug-panel__head">
            <div>
              <h3>响应结果</h3>
              <p>所有响应和错误都统一在这里展示。</p>
            </div>
            <el-tag v-if="testResult" :type="resultTagType" size="small">
              {{ testResult.status }}
              <template v-if="testResult.httpStatus != null"> · HTTP {{ testResult.httpStatus }}</template>
            </el-tag>
          </div>

          <div v-if="isStreaming" class="stream-status">
            <span class="stream-status__dot"/>
            <span>正在接收 · 区块 {{ streamBlocks.length }} · 已接收 {{ receivedBytesLabel }} · 已用 {{ elapsedLabel }}</span>
          </div>

          <el-skeleton v-if="loading && !isStreaming" :rows="5" animated/>
          <template v-else-if="testResult">
            <el-tabs v-model="responseTab" class="debug-tabs debug-tabs--response">
              <el-tab-pane label="概览" name="summary">
                <div v-if="summaryItems.length" class="result-meta">
                  <div v-for="item in summaryItems" :key="item.label" class="result-meta__item"
                       :class="{ 'result-meta__item--error': item.isError }">
                    <span class="result-meta__label">{{ item.label }}</span>
                    <span class="result-meta__value">{{ item.value }}</span>
                  </div>
                </div>
                <el-empty v-else description="无额外结果信息" :image-size="64"/>
                <div class="result-body">
                  <div class="result-body__title">完整响应体</div>
                  <ShellCodeBlock
                    v-if="resultBodyText"
                    class="response-body-code"
                    :code="resultBodyText"
                    language="json"
                    :copyable="true"
                  />
                  <el-empty v-else description="无响应内容" :image-size="64"/>
                </div>
              </el-tab-pane>
              <el-tab-pane label="Body" name="body" class="response-body-pane">
                <ShellCodeBlock
                  v-if="resultBodyText"
                  class="response-body-code"
                  :code="resultBodyText"
                  language="json"
                  :copyable="true"
                />
                <el-empty v-else description="无响应内容" :image-size="64"/>
              </el-tab-pane>
              <el-tab-pane label="流式响应" name="stream" class="response-stream-pane">
                <el-scrollbar ref="streamScrollbar" max-height="calc(100vh - 320px)" class="stream-blocks-scroll">
                  <div class="stream-blocks">
                    <div v-for="block in streamBlocks" :key="block.id" class="stream-block">
                      <div class="stream-block__head">
                        <span class="stream-block__index">#{{ block.id }}</span>
                        <span v-if="block.event" class="stream-block__event">event: {{ block.event }}</span>
                      </div>
                      <pre class="stream-block__code" v-html="highlightBlock(block.data)"></pre>
                    </div>
                    <div v-if="!streamBlocks.length" class="stream-blocks__empty">等待流式响应…</div>
                  </div>
                </el-scrollbar>
              </el-tab-pane>
            </el-tabs>
          </template>
          <el-empty v-else description="执行请求后在这里查看响应结果" :image-size="72"/>
        </section>
      </div>
    </el-scrollbar>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, reactive, ref, watch, type PropType} from "vue";
import JsonCodeEditor from "../common/JsonCodeEditor.vue";
import ShellCodeBlock from "../common/ShellCodeBlock.vue";
import {getModel} from "@/api/models";
import {getModelPool} from "@/api/modelPools";
import {testRoutingRule, testRoutingRuleStream} from "@/api/routing";
import {useModelTypes} from "@/composables/useModelTypes";
import {useDebugCapabilities} from "@/composables/useDebugCapabilities";
import {listTestRequestPresetOptions} from "@/api/routing";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {formatJson, formatJsonText, parseJsonResult, stringifyJson, highlightJsonHtml} from "@/utils/format";
import {readEventStream, type SseEvent} from "@/utils/sse";
import type {Dict, Row} from "@/types";

const GATEWAY_BASE_URL = import.meta.env.VITE_GATEWAY_BASE_URL || "http://127.0.0.1:9090";

// 协议名称来自后端 ModelApiProtocolEnum（/api/common/enums），前端不再维护 code->label 映射
const {labelOf: enumLabelOf} = useEnumOptions();

// 后端模型类型数据不可用时的通用兜底协议（对话类）
const DEFAULT_FALLBACK_PROTOCOLS = ["CHAT_COMPLETIONS", "RESPONSES", "MESSAGES", "COMPLETIONS"];

/** 路由规则的一个下游目标（模型 / 模型池） */
interface RoutingTarget {
  targetType?: string;
  targetId?: number | string | null;
  targetName?: string;
  targetCode?: string;
  primary?: boolean;
  /** 目标自身的模型类型，协议兜底时使用 */
  modelType?: string;
}

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  ruleId: {type: Number as PropType<number | null>, default: null},
  ruleName: {type: String, default: ""},
  ruleCode: {type: String, default: ""},
  secretKey: {type: String, default: ""},
  targets: {type: Array as PropType<RoutingTarget[]>, default: (): RoutingTarget[] => []}
});

const emit = defineEmits(["update:modelValue"]);

const {loadOptions: loadModelTypes, protocolsOf} = useModelTypes();
const {
  loadOptions: loadDebugCapabilities,
  canonicalOf,
  gatewayPathOf,
  hintOf
} = useDebugCapabilities();

const loading = ref(false);
const protocolLoading = ref(false);
const requestTab = ref("preset");
const responseTab = ref("summary");
const expandedPanels = ref<string[]>(["preview"]);

interface RoutingTestResult {
  curl?: string;
  status: string;
  httpStatus: number | string | null;
  modelCode: string | null;
  protocol: string | null;
  errorMessage: string;
  responseBody: string;
}

const testResult = ref<RoutingTestResult | null>(null);
const targetProtocolMap = reactive<Dict>({});
const templateStore = reactive<Dict>({});

// ===================== 流式测试状态 =====================
/** 正在进行的流式请求；非 null 时「发送请求」切换为「停止请求」 */
const streamAbortController = ref<AbortController | null>(null);
/** 流式累计接收的响应文本（原始顺序，不做中途格式化） */
const streamBody = ref("");
/** 已接收字节数，用于「正在接收」指示 */
const receivedBytes = ref(0);
/** 流开始时间戳，用于计算已用时 */
const streamStartedAt = ref<number | null>(null);
/** 触发重渲染的节流计时器：逐 token 直接改响应式属性会让高亮组件高频重算 */
let flushTimer: ReturnType<typeof setTimeout> | null = null;
/** 已收到但尚未刷入界面的片段 */
let pendingText = "";
/** 驱动「已用时」每秒重算的计时器 */
let elapsedTimer: ReturnType<typeof setInterval> | null = null;
const elapsedTick = ref(0);

// ===================== 流式区块（每收到一个事件追加一个区块） =====================
interface StreamBlock {
  id: number;
  event?: string;
  data: string;
}

/** 流式区块列表：每个 SSE 事件独立成块，逐块动态追加到「流式响应」页 */
const streamBlocks = ref<StreamBlock[]>([]);
/** 区块滚动容器引用，用于追加后自动滚到底部 */
const streamScrollbar = ref<ScrollbarLike | null>(null);
/** 已生成尚未刷入界面的区块（节流） */
let pendingBlocks: StreamBlock[] = [];
let blockSeq = 0;
/** 区块追加节流计时器，避免高频重排 */
let blockFlushTimer: ReturnType<typeof setTimeout> | null = null;

interface ScrollbarLike {
  wrapRef?: HTMLElement;
}

const isStreaming = computed(() => streamAbortController.value !== null);

const receivedBytesLabel = computed(() => {
  const bytes = receivedBytes.value;
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
});

const elapsedLabel = computed(() => {
  // 依赖 elapsedTick 才能随 setInterval 重算
  void elapsedTick.value;
  if (streamStartedAt.value == null) {
    return "0.0s";
  }
  return `${((Date.now() - streamStartedAt.value) / 1000).toFixed(1)}s`;
});

const resultTagType = computed(() => {
  if (testResult.value?.status === "RUNNING") {
    return "warning";
  }
  return testResult.value?.status === "SUCCESS" ? "success" : "danger";
});

/** 预设请求（按协议复用的完整请求体用例），调试面板下拉从接口载入 */
const presetOptions = ref<Array<{ id: number; name: string; protocolCode: string; requestBody: string }>>([]);
const selectedPresetId = ref<number | string | null>(null);

const testForm = reactive({
  secretKey: "",
  targetKey: "",
  protocol: ""
});

const drawerTitle = computed(() => {
  const name = props.ruleName?.trim();
  return name ? `路由规则测试 — ${name}` : "路由规则测试";
});

const targetOptions = computed(() =>
  (Array.isArray(props.targets) ? props.targets : [])
    .filter((item) => item?.targetId != null)
    .map((item) => ({
      ...item,
      key: targetKeyOf(item),
      label: targetLabel(item)
    }))
);

const activeTarget = computed(() => targetOptions.value.find((item) => item.key === testForm.targetKey) || null);
const availableProtocols = computed(() => {
  const protocols = targetProtocolMap[testForm.targetKey] || [];
  return protocols.map((code: string) => ({code, label: protocolLabel(code)}));
});
const activeProtocol = computed(() => normalizeProtocolCode(testForm.protocol));
const activeTargetLabel = computed(() => activeTarget.value?.label || "-");
const activeProtocolLabel = computed(() => protocolLabel(activeProtocol.value));
const activeTemplateKey = computed(() => `${testForm.targetKey || "default"}::${activeProtocol.value || "default"}`);
const filteredPresetOptions = computed(() => {
  const protocol = activeProtocol.value;
  if (!protocol) {
    return [];
  }
  return presetOptions.value.filter((preset) => preset.protocolCode === protocol);
});
const protocolHint = computed(() => hintOf(activeProtocol.value));

const currentTemplateText = computed({
  get() {
    return templateStore[activeTemplateKey.value] || "";
  },
  set(value) {
    templateStore[activeTemplateKey.value] = value;
  }
});

const requestUrl = computed(() => `${normalizeBaseUrl()}${protocolPath(activeProtocol.value)}`);
const requestHeadersText = computed(() => formatJson({
  Authorization: `Bearer ${testForm.secretKey?.trim() || "sk-your-routing-secret-key"}`,
  Rule: resolveRuleHeaderValue(),
  "Content-Type": "application/json"
}));
const parsedTemplateBody = computed(() => safeParseTemplate(currentTemplateText.value));
const curlPreview = computed(() => buildCurlCommand());
const displayCurl = computed(() => testResult.value?.curl || curlPreview.value);
const resultBodyText = computed(() => {
  // 流式阶段用原始文本（半截 JSON 格式化会报错），结束后再走 formatBody
  if (isStreaming.value) {
    return streamBody.value;
  }
  if (!testResult.value?.responseBody) {
    return "";
  }
  return formatBody(testResult.value.responseBody);
});
const summaryItems = computed(() => {
  if (!testResult.value) {
    return [];
  }
  const items: Array<{ label: string; value: unknown; isError?: boolean }> = [];
  if (testResult.value.protocol) {
    items.push({label: "接口协议", value: protocolLabel(testResult.value.protocol)});
  }
  if (testResult.value.modelCode) {
    items.push({label: "命中模型", value: testResult.value.modelCode});
  }
  if (isStreaming.value) {
    items.push({label: "已接收", value: receivedBytesLabel.value});
  }
  if (testResult.value.errorMessage) {
    items.push({label: "错误信息", value: testResult.value.errorMessage, isError: true});
  }
  return items;
});

watch(
  () => [props.modelValue, props.ruleId, props.secretKey],
  async ([visible]) => {
    if (!visible) {
      return;
    }
    testResult.value = null;
    requestTab.value = "preset";
    responseTab.value = "summary";
    expandedPanels.value = ["preview"];
    testForm.secretKey = props.secretKey || "";
    initializeTargetSelection();
    await Promise.all([loadModelTypes(), loadDebugCapabilities(), loadPresetOptions()]);
    await loadProtocolsForCurrentTarget();
  }
);

watch(
  () => testForm.targetKey,
  async (value, oldValue) => {
    if (!props.modelValue || !value || value === oldValue) {
      return;
    }
    await loadProtocolsForCurrentTarget();
  }
);

function initializeTargetSelection() {
  const primary = targetOptions.value.find((item) => item.primary) || targetOptions.value[0];
  testForm.targetKey = primary?.key || "";
}

function targetKeyOf(target: RoutingTarget): string {
  return `${normalizeTargetType(target.targetType)}:${target.targetId}`;
}

function normalizeTargetType(targetType: unknown): string {
  return String(targetType || "MODEL").trim().toUpperCase();
}

function targetLabel(target: RoutingTarget): string {
  const type = normalizeTargetType(target.targetType) === "MODEL_POOL" ? "模型池" : "模型";
  const name = target.targetName || target.targetCode || `#${target.targetId}`;
  return target.primary ? `${type}：${name}（主）` : `${type}：${name}`;
}

function protocolLabel(protocol: unknown): string {
  const code = normalizeProtocolCode(protocol);
  if (!code) return "-";
  return enumLabelOf("ModelApiProtocolEnum", code, code);
}

function protocolPath(protocol: unknown): string {
  // 网关调试路径由后端能力接口下发（与 RoutingRuleService.buildGatewayTestPath 同源）
  return gatewayPathOf(normalizeProtocolCode(protocol)) || "";
}

function normalizeProtocolCode(protocol: unknown): string {
  // 别名归一由后端能力接口的 canonicalCode 提供
  return canonicalOf(protocol);
}

/** 调试面板能否为该协议构造请求（无网关路径的协议如 CUSTOM 排除） */
function isDebuggableProtocol(protocol: unknown): boolean {
  return Boolean(gatewayPathOf(protocol));
}

function fallbackProtocolsForModelType(modelType: unknown): string[] {
  // 来源为后端 /api/models/types 的 supportedProtocols，这里只做通用清洗：
  // 归一化为 canonical 协议，并丢弃调试面板无法构造请求（无路径）的协议，如 CUSTOM
  const protocols = protocolsOf(modelType)
    .map((protocol: string) => normalizeProtocolCode(protocol))
    .filter(isDebuggableProtocol);
  return protocols.length ? Array.from(new Set(protocols)) : [...DEFAULT_FALLBACK_PROTOCOLS];
}

async function loadProtocolsForCurrentTarget() {
  const target = activeTarget.value;
  if (!target) {
    testForm.protocol = "";
    return;
  }
  protocolLoading.value = true;
  try {
    const detail = await resolveTargetProtocolDetail(target);
    const protocols = detail.length ? detail : fallbackProtocolsForModelType(target.modelType);
    targetProtocolMap[target.key] = protocols;
    if (!protocols.includes(normalizeProtocolCode(testForm.protocol))) {
      testForm.protocol = protocols[0] || "";
    } else {
      testForm.protocol = normalizeProtocolCode(testForm.protocol);
    }
    ensureTemplateForCurrentSelection();
  } finally {
    protocolLoading.value = false;
  }
}

async function resolveTargetProtocolDetail(target: RoutingTarget) {
  if (normalizeTargetType(target.targetType) === "MODEL_POOL") {
    return await loadModelPoolProtocols(target);
  }
  return await loadModelProtocols(target.targetId as string | number, target.modelType);
}

async function loadModelProtocols(modelId: number | string, modelType: string | undefined): Promise<string[]> {
  try {
    const detail = await getModel(modelId);
    const bindings = Array.isArray(detail?.apiBindings) ? detail.apiBindings : [];
    return normalizeProtocolsFromBindings(bindings, modelType);
  } catch {
    return fallbackProtocolsForModelType(modelType);
  }
}

async function loadModelPoolProtocols(target: RoutingTarget): Promise<string[]> {
  try {
    const detail = await getModelPool(target.targetId as string | number);
    const enabledItems: Row[] = (Array.isArray(detail?.items) ? detail.items : []).filter((item: Row) => item.enabled !== false);
    if (!enabledItems.length) {
      return fallbackProtocolsForModelType(target.modelType);
    }
    const protocolLists = await Promise.all(enabledItems.map((item: Row) => loadModelProtocols(item.modelId, item.modelType)));
    const protocols = intersectProtocolLists(protocolLists.filter((item: string[]) => item.length));
    return protocols.length ? protocols : fallbackProtocolsForModelType(target.modelType);
  } catch {
    return fallbackProtocolsForModelType(target.modelType);
  }
}

function normalizeProtocolsFromBindings(bindings: Row[], modelType: string | undefined): string[] {
  const protocols = bindings
    .filter((item: Row) => item?.enabled !== false)
    .map((item: Row) => normalizeProtocolCode(item.protocol))
    .filter(isDebuggableProtocol);
  return protocols.length ? Array.from(new Set(protocols)) : fallbackProtocolsForModelType(modelType);
}

function intersectProtocolLists(lists: string[][]): string[] {
  if (!lists.length) {
    return [];
  }
  let result = [...lists[0]];
  for (const list of lists.slice(1)) {
    result = result.filter((item) => list.includes(item));
  }
  return Array.from(new Set(result));
}

function ensureTemplateForCurrentSelection() {
  const protocol = activeProtocol.value;
  if (!protocol || !activeTemplateKey.value) {
    return;
  }
  selectedPresetId.value = null;
  if (!templateStore[activeTemplateKey.value]) {
    templateStore[activeTemplateKey.value] = createDefaultTemplate(protocol);
  }
}

function resetCurrentTemplate() {
  if (!activeProtocol.value) {
    return;
  }
  selectedPresetId.value = null;
  templateStore[activeTemplateKey.value] = createDefaultTemplate(activeProtocol.value);
}

async function loadPresetOptions() {
  try {
    const list = await listTestRequestPresetOptions();
    presetOptions.value = Array.isArray(list)
      ? (list as Dict[]).map((item) => ({
        id: item.id,
        name: item.name,
        protocolCode: item.protocolCode,
        requestBody: item.requestBody
      }))
      : [];
  } catch {
    presetOptions.value = [];
  }
}

function onPresetChange(presetId: number | string | null) {
  if (!presetId) {
    return;
  }
  const preset = presetOptions.value.find((item) => String(item.id) === String(presetId));
  if (!preset) {
    return;
  }
  const parsed = safeParseTemplate(preset.requestBody);
  const base = parsed.error ? {} : parsed.value;
  templateStore[activeTemplateKey.value] = formatJson(stripModelField(base));
}

function createDefaultTemplate(protocol: unknown): string {
  // 默认请求体由「预设请求」用例提供（替代原硬编码骨架）；同协议优先取非流式用例，否则回退最小结构
  const candidates = presetOptions.value.filter((preset) => preset.protocolCode === normalizeProtocolCode(protocol));
  // 同一协议同时提供流式与非流式用例时，默认保持非流式；用户可在下拉中主动选择流式版本。
  const candidate = candidates.find((preset) => {
    const parsed = safeParseTemplate(preset.requestBody);
    const body = parsed.value as Dict;
    return !parsed.error && body?.stream !== true;
  }) || candidates[0];
  const parsed = candidate ? safeParseTemplate(candidate.requestBody) : {error: true, value: {}};
  const base = parsed.error ? {} : parsed.value;
  if (candidate) {
    selectedPresetId.value = candidate.id;
  }
  return formatJson(stripModelField(base));
}

/** 客户端无需传 model：请求体的 model 由网关按路由目标的上游模型覆盖，这里一律剔除 */
function stripModelField(body: unknown): Dict {
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    return {};
  }
  const {model: _ignored, ...rest} = body as Dict;
  return rest;
}

function safeParseTemplate(text: unknown) {
  return parseJsonResult(text, {});
}

function normalizeBaseUrl() {
  const base = GATEWAY_BASE_URL.trim();
  return base.endsWith("/") ? base.slice(0, -1) : base;
}

function resolveRuleHeaderValue() {
  return props.ruleCode?.trim() || "your-rule-code";
}

function buildCurlCommand() {
  const secretKey = testForm.secretKey?.trim() || "sk-your-routing-secret-key";
  const json = parsedTemplateBody.value.error ? currentTemplateText.value || "{}" : stringifyJson(parsedTemplateBody.value.value || {});
  const escapedJson = json.replace(/'/g, "'\\''");
  return (
    `curl -X POST '${requestUrl.value}' \\\n` +
    `  -H 'Authorization: Bearer ${secretKey}' \\\n` +
    `  -H 'Rule: ${resolveRuleHeaderValue()}' \\\n` +
    `  -H 'Content-Type: application/json' \\\n` +
    `  -d '${escapedJson}'`
  );
}

function formatBody(body: unknown): string {
  if (!body) return "";
  return formatJsonText(body, 2, String(body));
}

/** 收集请求前置校验失败，统一的提示形态 */
function validationError(message: string): boolean {
  responseTab.value = "summary";
  testResult.value = {
    status: "ERROR",
    httpStatus: null,
    modelCode: null,
    protocol: activeProtocol.value || null,
    errorMessage: message,
    responseBody: ""
  };
  return false;
}

async function runTest() {
  if (!props.ruleId) return;
  if (!testForm.secretKey?.trim()) {
    validationError("请填写 API Key");
    return;
  }
  if (!activeTarget.value) {
    validationError("请选择调试目标");
    return;
  }
  if (!activeProtocol.value) {
    validationError("当前目标没有可调试的接口协议");
    return;
  }
  if (parsedTemplateBody.value.error) {
    responseTab.value = "summary";
    testResult.value = {
      status: "ERROR",
      httpStatus: null,
      modelCode: null,
      protocol: activeProtocol.value,
      errorMessage: "请求模板不是合法 JSON",
      responseBody: currentTemplateText.value || ""
    };
    return;
  }

  const requestBody = (parsedTemplateBody.value.value || {}) as Dict;
  // 提前取出已通过校验的目标与协议：runTest 的空值收窄不会传递到子函数
  const target = activeTarget.value;
  const protocol = activeProtocol.value;
  // stream=true 走增量展示，其余保持原有一次性 JSON 行为
  if (requestBody.stream === true) {
    await runStreamingTest(requestBody, target, protocol);
  } else {
    await runBufferedTest(requestBody, target, protocol);
  }
}

/** 非流式：直接请求网关原生 JSON 接口，避免经过管理端 /test 包装接口 */
async function runBufferedTest(requestBody: Dict, target: RoutingTarget, protocol: string) {
  loading.value = true;
  testResult.value = null;
  try {
    const response = await testRoutingRule(
      requestUrl.value,
      stripModelField(requestBody),
      testForm.secretKey.trim(),
      resolveRuleHeaderValue()
    );
    testResult.value = {
      status: "SUCCESS",
      httpStatus: response.status,
      modelCode: target.targetCode ?? null,
      protocol,
      curl: curlPreview.value,
      errorMessage: "",
      responseBody: serializeBody(response.data)
    };
    responseTab.value = "body";
  } catch (error) {
    responseTab.value = "body";
    testResult.value = normalizeErrorResult(error as Dict) as RoutingTestResult;
  } finally {
    loading.value = false;
  }
}

/** 流式：SSE 增量展示，meta/chunk/complete/error 分状态推进 */
async function runStreamingTest(requestBody: Dict, target: RoutingTarget, protocol: string) {
  // 重复点击时先中止上一条，避免两个流同时写同一个缓冲区
  abortStreaming();
  const controller = new AbortController();
  streamAbortController.value = controller;
  streamStartedAt.value = Date.now();
  receivedBytes.value = 0;
  streamBody.value = "";
  pendingText = "";
  streamBlocks.value = [];
  pendingBlocks = [];
  blockSeq = 0;
  clearBlockTimers();
  loading.value = true;
  if (elapsedTimer) {
    clearInterval(elapsedTimer);
  }
  elapsedTimer = setInterval(() => {
    elapsedTick.value += 1;
  }, 1000);
  // 先切到「流式响应」页并建立 RUNNING 态，避免等待期间显示空 skeleton
  responseTab.value = "stream";
  testResult.value = {
    status: "RUNNING",
    httpStatus: null,
    modelCode: null,
    protocol,
    curl: curlPreview.value,
    errorMessage: "",
    responseBody: ""
  };

  try {
    const gatewayBody = stripModelField(requestBody);
    const response = await testRoutingRuleStream(
      requestUrl.value,
      gatewayBody,
      testForm.secretKey.trim(),
      resolveRuleHeaderValue(),
      controller.signal
    );
    if (testResult.value) {
      testResult.value = {
        ...testResult.value,
        httpStatus: response.status,
        modelCode: target.targetCode || testResult.value.modelCode
      };
    }

    await readEventStream(response, handleStreamEvent, {abortSignal: controller.signal});
    flushStreamText();
    // 直连网关读取的是原生模型 SSE，没有管理端包装的 complete 事件；EOF 即成功结束。
    if (testResult.value?.status === "RUNNING") {
      finishStream("SUCCESS", "");
    }
  } catch (error) {
    flushStreamText();
    if (isAbortError(error)) {
      // 用户主动停止：保留已收到内容，不弹全局网络错误
      if (testResult.value?.status === "RUNNING") {
        finishStream("STOPPED", "");
      }
    } else {
      responseTab.value = "body";
      testResult.value = normalizeErrorResult(error as Dict) as RoutingTestResult;
    }
  } finally {
    if (flushTimer) {
      clearTimeout(flushTimer);
      flushTimer = null;
    }
    clearBlockTimers();
    if (elapsedTimer) {
      clearInterval(elapsedTimer);
      elapsedTimer = null;
    }
    pendingText = "";
    loading.value = false;
    streamAbortController.value = null;
    streamStartedAt.value = null;
  }
}

function handleStreamEvent(event: SseEvent) {
  // 流式测试现在直连网关，事件就是上游原生 SSE。
  // 每个事件独立成块追加到「流式响应」页，同时维护原始累计文本供 Body 页展示。
  const dataText = event.data ?? "";
  pendingBlocks.push({id: blockSeq++, event: event.event, data: dataText});
  scheduleBlockFlush();
  appendStreamText(formatUpstreamEvent(event));
}

/** 节流刷入区块：逐事件直接改响应式数组会让 v-html 高频重排，统一 60ms 批处理 */
function scheduleBlockFlush() {
  if (blockFlushTimer) {
    return;
  }
  blockFlushTimer = setTimeout(() => {
    blockFlushTimer = null;
    flushBlocks();
  }, 60);
}

function flushBlocks() {
  if (!pendingBlocks.length) {
    return;
  }
  streamBlocks.value = [...streamBlocks.value, ...pendingBlocks];
  pendingBlocks = [];
  scrollStreamToBottom();
}

function clearBlockTimers() {
  if (blockFlushTimer) {
    clearTimeout(blockFlushTimer);
    blockFlushTimer = null;
  }
  pendingBlocks = [];
}

function scrollStreamToBottom() {
  nextTick(() => {
    const wrap = streamScrollbar.value?.wrapRef;
    if (wrap) {
      wrap.scrollTop = wrap.scrollHeight;
    }
  });
}

/** 区块内代码高亮：复用 JSON 高亮（内部已做 HTML 转义，无 XSS 风险） */
function highlightBlock(data: string): string {
  if (!data) {
    return '<span class="sh-muted">（空）</span>';
  }
  return highlightJsonHtml(data);
}

function formatUpstreamEvent(event: SseEvent): string {
  const eventLine = event.event && event.event !== "message" ? `event: ${event.event}\n` : "";
  return `${eventLine}data: ${event.data}\n\n`;
}

/** 累积片段并节流刷新：逐 token 直接改响应式属性会让高亮组件高频重算 */
function appendStreamText(text: string) {
  if (!text) {
    return;
  }
  pendingText += text;
  receivedBytes.value += new TextEncoder().encode(text).length;
  if (flushTimer) {
    return;
  }
  flushTimer = setTimeout(() => {
    flushTimer = null;
    flushStreamText();
  }, 50);
}

function flushStreamText() {
  if (!pendingText) {
    return;
  }
  streamBody.value += pendingText;
  pendingText = "";
}

/** 结束流式态：把已收到的内容固化到 responseBody，并置终态 */
function finishStream(status: string, errorMessage: string) {
  if (testResult.value) {
    testResult.value = {
      ...testResult.value,
      status,
      errorMessage,
      responseBody: streamBody.value
    };
  }
}

function isAbortError(error: unknown): boolean {
  return (error as {name?: string})?.name === "AbortError";
}

/** 用户点击「停止请求」或关闭抽屉时调用 */
function abortStreaming() {
  if (streamAbortController.value) {
    streamAbortController.value.abort();
  }
}

function stopStreaming() {
  abortStreaming();
}

function normalizeErrorResult(error: Dict): Dict {
  const data = error?.response?.data;
  if (data && typeof data === "object") {
    if (typeof data.success === "boolean" && data.success === false) {
      return {
        status: "ERROR",
        httpStatus: error?.response?.status ?? null,
        modelCode: data.data?.modelCode ?? null,
        protocol: data.data?.protocol ?? activeProtocol.value,
        errorMessage: data.message || "请求失败",
        responseBody: serializeBody(data.data ?? data)
      };
    }
    return {
      status: "ERROR",
      httpStatus: error?.response?.status ?? null,
      modelCode: data.modelCode ?? null,
      protocol: data.protocol ?? activeProtocol.value,
      errorMessage:
        data.message ||
        (typeof data.error === "object" ? data.error?.message : data.error) ||
        error.message ||
        "请求失败",
      responseBody: serializeBody(data)
    };
  }
  return {
    status: "ERROR",
    httpStatus: error?.response?.status ?? null,
    modelCode: null,
    protocol: activeProtocol.value || null,
    errorMessage: error?.message || "网络错误",
    responseBody: ""
  };
}

function serializeBody(body: unknown): string {
  if (body == null || body === "") return "";
  if (typeof body === "string") return body;
  return stringifyJson(body);
}

function onClosed() {
  // 关闭抽屉必须中止在途流，否则网关连接与异步线程一直被占用
  abortStreaming();
  if (flushTimer) {
    clearTimeout(flushTimer);
    flushTimer = null;
  }
  clearBlockTimers();
  if (elapsedTimer) {
    clearInterval(elapsedTimer);
    elapsedTimer = null;
  }
  pendingText = "";
  streamBody.value = "";
  streamBlocks.value = [];
  receivedBytes.value = 0;
  streamStartedAt.value = null;
  testResult.value = null;
  loading.value = false;
}

onBeforeUnmount(() => {
  abortStreaming();
  if (flushTimer) {
    clearTimeout(flushTimer);
    flushTimer = null;
  }
  clearBlockTimers();
  if (elapsedTimer) {
    clearInterval(elapsedTimer);
    elapsedTimer = null;
  }
});
</script>

<style scoped>
.routing-test {
  display: grid;
  gap: 12px;
}

.debug-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
  padding: 12px;
}

.debug-panel--request {
  background: var(--el-fill-color-blank);
}

.debug-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.debug-panel__head h3 {
  margin: 0 0 4px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.debug-panel__head p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.request-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.request-toolbar__main {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  flex: 1;
}

.request-toolbar__method {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  border-radius: 6px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
}

.request-toolbar__url {
  min-width: 0;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
  color: var(--el-text-color-regular);
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 34px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.request-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}

.test-form {
  margin-bottom: 0;
}

.test-form__row {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(240px, 1fr);
  gap: 12px;
}

.test-form__row--three {
  grid-template-columns: minmax(0, 1.2fr) minmax(220px, 1fr) minmax(220px, 1fr);
}

.test-form__row :deep(.el-form-item) {
  margin-bottom: 10px;
}

.request-template {
  margin-top: 2px;
}

.preset-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.preset-row .preset-select {
  flex: 1;
  min-width: 0;
}

.request-template__hint {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.debug-collapse {
  margin-top: 10px;
}

.debug-collapse :deep(.el-collapse-item__header) {
  height: 34px;
  color: var(--el-text-color-regular);
  font-size: 12px;
}

.debug-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: 0;
}

.debug-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 0;
}

.debug-panel--response {
  min-height: 520px;
  display: flex;
  flex-direction: column;
}

.debug-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.debug-tabs :deep(.el-tabs__item) {
  font-size: 12px;
}

/* 请求预览按内容自适应，不撑出大片空白 */
.debug-tabs--preview :deep(.el-tabs__content) {
  min-height: 0;
}

.debug-tabs--response {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.debug-tabs--response :deep(.el-tabs__content) {
  flex: 1;
  min-height: 360px;
}

.debug-tabs--response :deep(.el-tab-pane) {
  height: 100%;
}

.response-body-pane {
  height: 100%;
}

.response-body-code {
  height: 100%;
}

.response-body-code :deep(.shell-code-wrap) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.response-body-code :deep(.code-block-view__scrollbar) {
  flex: 1;
  /* 必须 !important：CodeBlockView 的 max-height 由 prop 生成内联 style，
     普通 class 覆写压不过内联，故 Body 高度被锁死默认上限。!important 才能破除 */
  max-height: none !important;
}

.response-body-code :deep(.el-scrollbar__wrap) {
  max-height: none !important;
}

.result-meta {
  display: grid;
  gap: 8px;
  margin-bottom: 12px;
}

.result-meta__item {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.result-meta__label {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 1.4;
}

.result-meta__value {
  min-width: 0;
  color: var(--el-text-color-primary);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.5;
  word-break: break-all;
}

.result-meta__item--error {
  background: var(--el-color-danger-light-9);
}

/* 概览 tab 内的完整响应体区块 */
.result-body {
  margin-top: 4px;
}

.result-body__title {
  margin-bottom: 8px;
  color: var(--el-text-color-regular);
  font-size: 12px;
  font-weight: 600;
}

.result-meta__item--error .result-meta__value {
  color: var(--el-color-danger);
}

/* 流式接收指示：呼吸点 + 实时字节/耗时 */
.stream-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
  font-size: 12px;
  line-height: 1.5;
}

.stream-status__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-warning);
  animation: stream-pulse 1.2s ease-in-out infinite;
}

@keyframes stream-pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.25;
  }
}

/* 流式响应：每收到一个事件追加一个独立区块 */
.response-stream-pane {
  height: 100%;
}

.stream-blocks-scroll {
  height: 100%;
}

.stream-blocks {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px;
}

.stream-block {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  overflow: hidden;
  background: #1e1e1e;
}

.stream-block__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 10px;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 11px;
}

.stream-block__index {
  font-weight: 700;
  color: var(--el-color-primary);
  font-family: Consolas, "Courier New", monospace;
}

.stream-block__event {
  color: var(--el-text-color-secondary);
  font-family: Consolas, "Courier New", monospace;
}

.stream-block__code {
  margin: 0;
  padding: 10px 12px;
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-all;
}

.stream-block__code :deep(.json-key) {
  color: #9cdcfe;
}

.stream-block__code :deep(.json-string) {
  color: #ce9178;
}

.stream-block__code :deep(.json-number) {
  color: #b5cea8;
}

.stream-block__code :deep(.json-boolean),
.stream-block__code :deep(.json-null) {
  color: #569cd6;
}

.stream-block__code :deep(.sh-muted) {
  color: #808080;
}

.stream-blocks__empty {
  padding: 24px;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (max-width: 900px) {
  .request-toolbar,
  .test-form__row,
  .test-form__row--three {
    grid-template-columns: 1fr;
  }

  .request-toolbar__main {
    grid-template-columns: 1fr;
  }

  .request-toolbar__method {
    width: 72px;
  }

  .debug-panel--response {
    min-height: 420px;
  }
}
</style>
