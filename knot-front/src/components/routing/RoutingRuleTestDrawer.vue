<template>
  <el-drawer
    :model-value="modelValue"
    :title="drawerTitle"
    size="1000px"
    class="drawer-with-scrollbar routing-test-drawer"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >
    <el-scrollbar max-height="calc(100vh - 140px)">
      <div class="slot-body routing-test">
        <section class="debug-panel debug-panel--request">
          <div class="request-toolbar">
            <el-select
              v-model="testForm.targetKey"
              placeholder="选择调试目标"
              filterable
              class="request-toolbar__target"
              @change="onTargetChange"
            >
              <el-option label="全部目标" :value="ALL_TARGETS_KEY" />
              <el-option
                v-for="target in targetOptions"
                :key="target.key"
                :label="target.label"
                :value="target.key"
              />
            </el-select>
            <el-select
              v-model="testForm.protocol"
              :loading="protocolLoading"
              :disabled="availableProtocols.length === 0"
              placeholder="选择接口协议"
              class="request-toolbar__protocol"
              @change="ensureTemplateForCurrentSelection"
            >
              <el-option
                v-for="protocol in availableProtocols"
                :key="protocol.code"
                :label="protocol.label"
                :value="protocol.code"
              />
            </el-select>
            <div class="request-toolbar__url">{{ requestUrl }}</div>
            <el-tooltip v-if="!isStreaming" content="发送请求" placement="top">
              <el-button
                type="primary"
                :icon="Promotion"
                :loading="loading"
                :disabled="!ruleId"
                @click="runTest"
              />
            </el-tooltip>
            <el-tooltip v-else content="停止请求" placement="top">
              <el-button type="danger" :icon="CircleClose" @click="stopStreaming" />
            </el-tooltip>
          </div>

          <div class="test-billing-verify-toggle">
            <el-checkbox v-model="testBillingVerify" size="small">执行计费规则校验</el-checkbox>
            <span class="test-billing-verify-toggle__hint">{{ billingVerifyHint }}</span>
            <el-tag
              v-if="consumerReturnsUsage"
              size="small"
              type="success"
              effect="plain"
              class="test-billing-verify-toggle__tag"
            >消费者已默认返回消费详情</el-tag>
          </div>

          <el-form label-width="0px" class="test-form">
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
                        height="320px"
                      />
                      <div v-if="protocolHint" class="request-template__hint">{{ protocolHint }}</div>
                    </div>
                  </el-tab-pane>
                  <el-tab-pane label="Headers" name="headers">
                    <div class="header-table-toolbar">
                      <span class="header-table-toolbar__hint">表格即实际请求头：新增 / 编辑 / 删除后随请求发往网关，curl 预览同步更新</span>
                      <el-button size="small" :icon="Plus" @click="addHeaderRow">新增 Header</el-button>
                    </div>
                    <el-table
                      :data="displayHeaderRows"
                      border
                      size="small"
                      class="header-table"
                      empty-text="暂无请求头，点击右上角「新增 Header」添加"
                    >
                      <el-table-column label="名称" min-width="220">
                        <template #default="{ row }">
                          <el-input
                            v-if="!row.temporary"
                            v-model="row.name"
                            placeholder="如 X-Custom-Trace"
                            size="small"
                            @change="normalizeHeaderRow(row)"
                          />
                          <span v-else>{{ row.name }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column label="值" min-width="260">
                        <template #default="{ row }">
                          <el-input v-if="!row.temporary" v-model="row.value" placeholder="Header 值" size="small"/>
                          <span v-else>{{ row.value }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column label="操作" width="70" align="center">
                        <template #default="{ row }">
                          <el-button link type="danger" :disabled="row.temporary" @click="removeHeaderRow(row)">
                            <el-icon>
                              <Delete/>
                            </el-icon>
                          </el-button>
                        </template>
                      </el-table-column>
                    </el-table>
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
                <div v-if="summaryItems.length || displayReasoning || displayContent" class="result-meta">
                  <div v-for="item in summaryItems" :key="item.label" class="result-meta__item"
                       :class="{ 'result-meta__item--error': item.isError }">
                    <span class="result-meta__label">{{ item.label }}</span>
                    <span class="result-meta__value">{{ item.value }}</span>
                  </div>
                  <div v-if="displayReasoning" class="result-meta__item">
                    <span class="result-meta__label">思考过程 · reasoning_content</span>
                    <pre class="result-meta__value result-meta__value--block">{{ displayReasoning }}</pre>
                  </div>
                  <div v-if="displayContent" class="result-meta__item">
                    <span class="result-meta__label">完整内容 · content</span>
                    <pre class="result-meta__value result-meta__value--block">{{ displayContent }}</pre>
                  </div>
                </div>
                <el-empty v-else description="无结果信息" :image-size="64"/>
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
              <el-tab-pane v-if="showStreamTab" label="流式响应" name="stream" class="response-stream-pane">
                <el-scrollbar ref="streamScrollbar" max-height="calc(100vh - 320px)" class="stream-blocks-scroll">
                  <div class="stream-blocks">
                    <div v-for="block in streamBlocks" :key="block.id" class="stream-block">
                      <div class="stream-block__head">
                        <span class="stream-block__index">#{{ block.id }}</span>
                        <span v-if="block.event" class="stream-block__event">event: {{ block.event }}</span>
                        <el-tooltip
                          :content="copiedBlockId === block.id ? '已复制' : '复制'"
                          placement="top"
                          :show-after="300"
                        >
                          <el-button
                            class="stream-block__copy"
                            size="small"
                            text
                            type="primary"
                            :aria-label="copiedBlockId === block.id ? '已复制' : '复制'"
                            @click="copyBlock(block)"
                          >
                            <el-icon><Select v-if="copiedBlockId === block.id"/><CopyDocument v-else/></el-icon>
                          </el-button>
                        </el-tooltip>
                      </div>
                      <pre class="stream-block__code" v-html="highlightBlock(block.data)"></pre>
                    </div>
                    <div v-if="!streamBlocks.length" class="stream-blocks__empty">等待流式响应…</div>
                  </div>
                </el-scrollbar>
              </el-tab-pane>
              <el-tab-pane label="Header" name="header">
                <div v-if="responseHeaderItems.length" class="result-meta">
                  <div v-for="item in responseHeaderItems" :key="item.label" class="result-meta__item">
                    <span class="result-meta__label">{{ item.label }}</span>
                    <span class="result-meta__value">{{ item.value }}</span>
                  </div>
                </div>
                <el-empty v-else description="无响应头" :image-size="64"/>
              </el-tab-pane>
              <!-- 计费校验（JEV 双盲）：测试响应后自动触发，三区展示，不阻塞上方结果（v7 §9） -->
              <el-tab-pane v-if="verdictVisible" label="计费校验" name="verdict">
                <RoutingBillingVerdictPanel
                  :result="verdictResult"
                  :loading="verdictLoading"
                  :skipped="verdictSkipped"
                  :error="verdictError"
                />
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
import {CircleClose, CopyDocument, Delete, Plus, Promotion, Select} from "@element-plus/icons-vue";
import {ElMessage} from "element-plus";
import JsonCodeEditor from "../common/JsonCodeEditor.vue";
import ShellCodeBlock from "../common/ShellCodeBlock.vue";
import RoutingBillingVerdictPanel from "./RoutingBillingVerdictPanel.vue";
import {
  listRoutingRuleDebugProtocols,
  testRoutingRule,
  testRoutingRuleBillingVerdict,
  testRoutingRuleStream,
  type RoutingBillingVerdictRequestPayload,
  type RoutingBillingVerdictResult
} from "@/api/routing";
import {useDebugCapabilities} from "@/composables/useDebugCapabilities";
import {useCopyToClipboard} from "@/composables/useCopyToClipboard";
import {getTestRequestPreset, listTestRequestPresetOptions} from "@/api/routing";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {formatJson, formatJsonText, parseJsonResult, stringifyJson, highlightJsonHtml} from "@/utils/format";
import {type SseEvent} from "@/utils/sse";
import type {Dict} from "@/types";

const GATEWAY_BASE_URL = import.meta.env.VITE_GATEWAY_BASE_URL || "http://127.0.0.1:9090";

/** 管理面测试头：勾选「测试时临时开启返回消费详情」后随请求发往网关（与后端 GatewayHeaders 同款常量） */
const TEST_RETURN_USAGE_HEADER = "X-Knot-Test-Return-Usage";
/** 网关自产扩展字段：整包响应体顶层 / 流式 [DONE] 前独立 SSE 事件（与后端 AiPayloadFields.KNOT_EXTEND 对应） */
const KNOT_EXTEND_FIELD = "knot_extend";
/** 与 knot_extend 并列的上游原始 usage 透传字段名（后端 AiPayloadFields.RAW_USAGE，整包/流式同键） */
const RAW_USAGE_FIELD = "usage";
/** 实际承接模型响应头（fetch Headers 遍历统一小写；后端 GatewayHeaders.X_KNOT_MODEL） */
const MODEL_RESPONSE_HEADER = "x-knot-model";

// 协议名称来自后端 ModelApiProtocolEnum（/api/enum-options），前端不再维护 code->label 映射
const {labelOf: enumLabelOf} = useEnumOptions();

/** 「全部目标」哨兵 key：协议下拉展示所有目标协议的并集（否则仅展示所选目标的协议） */
const ALL_TARGETS_KEY = "__ALL__";

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
  targets: {type: Array as PropType<RoutingTarget[]>, default: (): RoutingTarget[] => []},
  /** 规则绑定消费者自身的「返回消费详情」开关：已开启时无需（也不显示）临时开启勾选 */
  consumerReturnUsageDetail: {type: Boolean, default: false}
});

const emit = defineEmits(["update:modelValue"]);

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
/** 最近一次响应的响应头，供「响应 Header」tab 展示 */
const responseHeaders = ref<Record<string, string>>({});
const targetProtocolMap = reactive<Dict>({});
const templateStore = reactive<Dict>({});

// ===================== 计费裁判（JEV 双盲校验，v7 §3.2 两步走） =====================
/** 当次响应提取的 knot_extend 被告负载 {usage, billing}；null = 本次响应无消费详情 */
const knotExtend = ref<Dict | null>(null);
/** 与 knot_extend 并列的上游原始 usage 原文（裁判证据） */
const upstreamUsage = ref<Dict | null>(null);
const verdictResult = ref<RoutingBillingVerdictResult | null>(null);
const verdictLoading = ref(false);
/** 本次响应无 knot_extend：链路短路，未发起裁判（v7 §3.1 降级提示） */
const verdictSkipped = ref(false);
const verdictError = ref("");
/** 裁判轮次令牌：新测试发起即自增，过期结果丢弃，避免连续测试结果串台 */
let verdictToken = 0;

/** 面板可见性：开关开启且任一裁判态激活即渲染（skipped / error 也是设计内的展示态） */
const verdictVisible = computed(
  () =>
    testBillingVerify.value &&
    (verdictLoading.value || verdictSkipped.value || verdictError.value !== "" || verdictResult.value !== null)
);
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

/** 最近一次成功复制的区块 id，用于按钮瞬时反馈；由 copyResetTimer 延时复位 */
const copiedBlockId = ref<number | null>(null);
let copyResetTimer: ReturnType<typeof setTimeout> | null = null;
const {copy} = useCopyToClipboard();

/** 复制单个流式区块的原始 SSE data 文本 */
async function copyBlock(block: StreamBlock) {
  const ok = await copy(block.data, "该区块暂无内容");
  if (!ok) {
    return;
  }
  copiedBlockId.value = block.id;
  if (copyResetTimer) {
    clearTimeout(copyResetTimer);
  }
  copyResetTimer = setTimeout(() => {
    copyResetTimer = null;
    copiedBlockId.value = null;
  }, 1200);
}

// ===================== 流式内容拼装（概览页展示完整 content / reasoning_content） =====================
/** 流式拼装的完整 content（多包 delta.content 累加） */
const streamContent = ref("");
/** 流式拼装的完整 reasoning_content（思考过程，与 content 分开展示） */
const streamReasoning = ref("");
/** 已提取但尚未刷入界面的 content / reasoning 片段（复用区块节流计时器） */
let pendingContent = "";
let pendingReasoning = "";

const isStreaming = computed(() => streamAbortController.value !== null);

/** 请求模板是否声明 stream:true（非流式请求不展示「流式响应」tab） */
const requestIsStreaming = computed(() => {
  const parsed = parsedTemplateBody.value;
  if (parsed.error) {
    return false;
  }
  const body = parsed.value as { stream?: unknown } | null;
  return body?.stream === true;
});
/** 仅流式进行中或请求为流式时才展示「流式响应」tab，避免非流式响应下出现无内容的空页 */
const showStreamTab = computed(() => isStreaming.value || requestIsStreaming.value);

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
const presetOptions = ref<Array<{ id: number; name: string; protocolCode: string }>>([]);
const selectedPresetId = ref<number | string | null>(null);

const testForm = reactive({
  secretKey: "",
  targetKey: "",
  protocol: ""
});

/**
 * 「执行计费规则校验」开关：合并了"测试时临时开启返回消费详情"——后者是前者的前提子集。
 * 勾选后：① 若绑定消费者自身未开启返回消费详情，则本次请求自动附带 X-Knot-Test-Return-Usage（knot_extend 数据）；
 *         ② 测试响应后自动发起 JEV 双盲计费校验。
 * 关闭后：既不请求消费详情，也不跑校验，不渲染「计费校验」页，但响应照常返回。
 * 默认开启——与抽屉既有行为一致。
 */
const testBillingVerify = ref(true);

/**
 * 区分「绑定消费者已默认返回消费详情」与「未开启」两类规则：
 * - 已默认支持：knot_extend 本来就在响应里，开关只控制是否发起 JEV 校验；
 * - 未开启：勾选开关时本次请求临时附带 X-Knot-Test-Return-Usage 以拿到校验数据。
 */
const consumerReturnsUsage = computed(() => props.consumerReturnUsageDetail);
const billingVerifyHint = computed(() =>
  consumerReturnsUsage.value
    ? "勾选后响应自动发起 JEV 双盲计费校验。关闭则跳过校验，不展示「计费校验」"
    : "勾选后：本次请求自动附带 knot_extend（用量与网关计费明细），响应后发起 JEV 双盲计费校验"
);

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
  // 「全部目标」= 所有目标协议并集；选定单一目标 = 该目标协议。多目标规则下各目标协议可能不同
  // （如主目标 CHAT_COMPLETIONS、次目标 RESPONSES），只取主目标会漏掉后者。
  let protocols: string[];
  if (testForm.targetKey === ALL_TARGETS_KEY) {
    protocols = Array.from(new Set(Object.values(targetProtocolMap).flat() as string[]));
  } else {
    protocols = (targetProtocolMap[testForm.targetKey] as string[]) || [];
  }
  return protocols.map((code: string) => ({code, label: protocolLabel(code)}));
});
const activeProtocol = computed(() => canonicalOf(testForm.protocol));
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

// ===================== 请求头表格（Headers tab 即权威） =====================
/** 请求头表格行：抽屉打开时按当前密钥 / 规则编码生成默认三行，之后以表格为准 */
interface HeaderRow {
  name: string;
  value: string;
}

const headerRows = ref<HeaderRow[]>([]);

interface HeaderDisplayRow extends HeaderRow {
  temporary?: boolean;
}

const displayHeaderRows = computed<HeaderDisplayRow[]>(() => {
  const rows: HeaderDisplayRow[] = [...headerRows.value];
  if (testBillingVerify.value && !props.consumerReturnUsageDetail && !hasHeaderName(TEST_RETURN_USAGE_HEADER)) {
    rows.push({name: TEST_RETURN_USAGE_HEADER, value: "true", temporary: true});
  }
  return rows;
});

/** 打开抽屉（或重置）时生成默认请求头：Authorization / Rule / Content-Type */
function resetHeaderRows() {
  headerRows.value = [
    {name: "Authorization", value: `Bearer ${testForm.secretKey?.trim() || "sk-your-routing-secret-key"}`},
    {name: "Rule", value: resolveRuleHeaderValue()},
    {name: "Content-Type", value: "application/json"}
  ];
}

function addHeaderRow() {
  headerRows.value.push({name: "", value: ""});
}

function removeHeaderRow(row: unknown) {
  const index = headerRows.value.findIndex((item) => item === row);
  if (index >= 0) {
    headerRows.value.splice(index, 1);
  }
}

/** 名称失焦规范化：trim；重名即时警告（不阻断编辑，发送前还会硬校验） */
function normalizeHeaderRow(row: unknown) {
  if (!row || typeof row !== "object" || !("name" in row) || typeof row.name !== "string") {
    return;
  }
  const headerRow = row as HeaderRow;
  headerRow.name = headerRow.name.trim();
  if (!headerRow.name) {
    return;
  }
  const duplicated = headerRows.value.some(
    (item) => item !== headerRow && item.name.trim().toLowerCase() === headerRow.name.toLowerCase()
  );
  if (duplicated) {
    ElMessage.warning(`请求头「${headerRow.name}」已存在，重名只有最后一条会生效`);
  }
}

/** 表格中是否已存在同名头（HTTP 头大小写不敏感） */
function hasHeaderName(name: string): boolean {
  const lower = name.toLowerCase();
  return headerRows.value.some((row) => row.name.trim().toLowerCase() === lower);
}

/** 发送前校验：名称trim后不可重名（大小写不敏感），空名称行直接跳过 */
function validateHeaderRows(): string | null {
  const seen = new Set<string>();
  for (const row of headerRows.value) {
    const name = row.name.trim();
    if (!name) {
      continue;
    }
    const lower = name.toLowerCase();
    if (seen.has(lower)) {
      return `请求头「${name}」重名，请合并为一行`;
    }
    seen.add(lower);
  }
  return null;
}

/**
 * 组装实际请求头：表格为权威（删除的行即不发送）；
 * 协议必需头 Content-Type / Accept 仅在表格缺失时补默认，表格填了则以表格为准。
 * 勾选临时开启时补测试头（表格已手动填同名头则不覆盖，保持表格权威）。
 */
function buildRequestHeaders(accept: string): Record<string, string> {
  const headers: Record<string, string> = {};
  for (const row of headerRows.value) {
    const name = row.name.trim();
    if (name) {
      headers[name] = row.value;
    }
  }
  if (!hasHeaderName("Accept")) {
    headers.Accept = accept;
  }
  if (!hasHeaderName("Content-Type")) {
    headers["Content-Type"] = "application/json";
  }
  if (testBillingVerify.value && !props.consumerReturnUsageDetail && !hasHeaderName(TEST_RETURN_USAGE_HEADER)) {
    headers[TEST_RETURN_USAGE_HEADER] = "true";
  }
  return headers;
}
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

/** 概览页「完整内容」：优先用流式拼装的 content，否则从非流式响应体反解 */
const displayContent = computed(() => {
  if (streamContent.value) {
    return streamContent.value;
  }
  const body = testResult.value?.responseBody;
  if (!body) {
    return "";
  }
  const parsed = parseJsonResult(body, null);
  if (parsed.error) {
    return "";
  }
  // 主流对话形态取 content；legacy completions 的 choices[].text 兜底
  return extractField(parsed.value, "content") || extractField(parsed.value, "text");
});

/** 概览页「思考过程」：reasoning_content，与 content 分开 */
const displayReasoning = computed(() => {
  if (streamReasoning.value) {
    return streamReasoning.value;
  }
  const body = testResult.value?.responseBody;
  if (!body) {
    return "";
  }
  const parsed = parseJsonResult(body, null);
  return parsed.error ? "" : extractField(parsed.value, "reasoning_content");
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

/** 响应头：转为与概览同款卡片所需的 {label, value} 列表 */
const responseHeaderItems = computed(() =>
  Object.entries(responseHeaders.value).map(([label, value]) => ({label, value}))
);

// ===================== 计费裁判：被告提取与自动触发 =====================

/** 重置裁判区：新测试 / 抽屉开关时调用，令牌自增使在途请求的结果作废 */
function resetVerdictState() {
  verdictToken += 1;
  knotExtend.value = null;
  upstreamUsage.value = null;
  verdictResult.value = null;
  verdictLoading.value = false;
  verdictSkipped.value = false;
  verdictError.value = "";
}

/**
 * 提取 knot_extend 被告负载与并列的上游原始 usage（v7 §4.1）：
 * 整包响应 = 响应体顶层字段；流式 = [DONE] 前网关注入的独立 SSE 事件（同构 JSON）。
 * 无 knot_extend 字段时静默忽略（消费者未开返回消费详情且未勾选临时开启）。
 */
function captureKnotExtend(payload: unknown) {
  const extend = findObjectField(payload, KNOT_EXTEND_FIELD);
  if (!extend || typeof extend !== "object" || Array.isArray(extend)) {
    return;
  }
  knotExtend.value = extend as Dict;
  const raw = findSiblingField(payload, KNOT_EXTEND_FIELD, RAW_USAGE_FIELD);
  upstreamUsage.value = raw && typeof raw === "object" ? (raw as Dict) : null;
}

/** 网关插入的 knot_extend 行可能与紧随其后的 [DONE] 共处一个 SSE 事件。 */
function captureKnotExtendFromStreamData(data: string) {
  const parsed = parseStreamJson(data);
  if (!parsed.error && parsed.value && typeof parsed.value === "object") {
    captureKnotExtend(parsed.value);
    if (knotExtend.value) {
      return;
    }
  }
  for (const line of data.split(/\r?\n/)) {
    const lineParsed = parseStreamJson(line);
    if (!lineParsed.error && lineParsed.value && typeof lineParsed.value === "object") {
      captureKnotExtend(lineParsed.value);
      if (knotExtend.value) {
        return;
      }
    }
  }
}

function parseStreamJson(value: string) {
  const text = value.trim();
  const jsonText = text.startsWith("data:") ? text.slice("data:".length).trim() : text;
  return parseJsonResult(jsonText, null);
}

/** 兼容 Responses 等协议把事件包在 response 子对象中的返回形态。 */
function findObjectField(payload: unknown, field: string): unknown {
  if (typeof payload === "string") {
    const parsed = parseJsonResult(payload, null);
    return parsed.error ? null : findObjectField(parsed.value, field);
  }
  if (!payload || typeof payload !== "object") {
    return null;
  }
  if (Array.isArray(payload)) {
    for (const item of payload) {
      const found = findObjectField(item, field);
      if (found != null) {
        return found;
      }
    }
    return null;
  }
  const dict = payload as Dict;
  if (Object.prototype.hasOwnProperty.call(dict, field)) {
    return dict[field];
  }
  for (const value of Object.values(dict)) {
    const found = findObjectField(value, field);
    if (found != null) {
      return found;
    }
  }
  return null;
}

function findSiblingField(payload: unknown, anchorField: string, field: string): unknown {
  if (!payload || typeof payload !== "object") {
    return null;
  }
  if (Array.isArray(payload)) {
    for (const item of payload) {
      const found = findSiblingField(item, anchorField, field);
      if (found != null) {
        return found;
      }
    }
    return null;
  }
  const dict = payload as Dict;
  if (Object.prototype.hasOwnProperty.call(dict, anchorField)) {
    return dict[field];
  }
  for (const value of Object.values(dict)) {
    const found = findSiblingField(value, anchorField, field);
    if (found != null) {
      return found;
    }
  }
  return null;
}

/** 实际承接模型：优先响应头 X-Knot-Model，缺失回退当前所选目标（裁判据此前端定位规则与提取器） */
function servingModelCode(): string | null {
  const header = responseHeaders.value[MODEL_RESPONSE_HEADER] || responseHeaders.value["X-Knot-Model"];
  const code = header?.trim();
  return code || activeTarget.value?.targetCode || null;
}

/**
 * 计费裁判第二步（v7 §3.2）：测试响应到手后自动发起，不阻塞主流程。
 * knot_extend 缺席 → skipped 短路提示；请求失败 → error 降级展示；两者均不影响已渲染的测试结果。
 * 不发第二次上游请求——被告就是已在抽屉里显示过的那次调用的产出（v7 §4.1 纪律）。
 */
async function triggerBillingVerdict(requestBody: Dict, stream: boolean) {
  const token = verdictToken;
  if (!props.ruleId) {
    return;
  }
  if (!knotExtend.value) {
    verdictSkipped.value = true;
    return;
  }
  verdictLoading.value = true;
  verdictError.value = "";
  try {
    const payload: RoutingBillingVerdictRequestPayload = {
      modelCode: activeTarget.value?.targetCode ?? null,
      targetCode: servingModelCode(),
      protocol: activeProtocol.value || null,
      stream,
      knotExtend: knotExtend.value,
      upstreamUsage: upstreamUsage.value ?? {},
      requestBody,
      sessionId: null
    };
    const result = await testRoutingRuleBillingVerdict(props.ruleId, payload);
    if (token !== verdictToken) {
      return;
    }
    verdictResult.value = result;
  } catch (error) {
    if (token !== verdictToken) {
      return;
    }
    const err = error as Dict;
    verdictError.value = String(err?.response?.data?.message || err?.message || "计费校验请求失败");
  } finally {
    if (token === verdictToken) {
      verdictLoading.value = false;
    }
  }
}

watch(
  () => [props.modelValue, props.ruleId, props.secretKey],
  async ([visible]) => {
    if (!visible) {
      return;
    }
    testResult.value = null;
    responseHeaders.value = {};
    resetVerdictState();
    requestTab.value = "preset";
    responseTab.value = "summary";
    expandedPanels.value = ["preview"];
    testBillingVerify.value = true;
    testForm.secretKey = props.secretKey || "";
    resetHeaderRows();
    initializeTargetSelection();
    await Promise.all([loadDebugCapabilities(), loadPresetOptions()]);
    await loadAllTargetProtocols();
  }
);

watch(
  () => testForm.targetKey,
  async (value, oldValue) => {
    if (!props.modelValue || !value || value === oldValue) {
      return;
    }
    onTargetChange();
  }
);

watch(showStreamTab, (visible) => {
  // 切到非流式请求后「流式响应」tab 消失，若当前激活项正停在该 tab，回退到 Body 避免空内容区
  if (!visible && responseTab.value === "stream") {
    responseTab.value = "body";
  }
});

function initializeTargetSelection() {
  // 默认「全部目标」：协议下拉给出所有目标协议的并集，避免只暴露主目标协议、
  // 次目标（如 RESPONSES）永远无法调试；用户仍可在下拉里锁定单个目标。
  testForm.targetKey = ALL_TARGETS_KEY;
}

function targetKeyOf(target: RoutingTarget): string {
  return `${normalizeTargetType(target.targetType)}:${target.targetId}`;
}

function normalizeTargetType(targetType: unknown): string {
  return String(targetType || "MODEL").trim().toUpperCase();
}

function targetLabel(target: RoutingTarget): string {
  const name = target.targetName || target.targetCode || `#${target.targetId}`;
  return target.primary ? `${name}（主）` : `${name}`;
}

function protocolLabel(protocol: unknown): string {
  const code = canonicalOf(protocol);
  if (!code) return "-";
  return enumLabelOf("ModelApiProtocolEnum", code, code);
}

function protocolPath(protocol: unknown): string {
  // 网关调试路径由后端能力接口下发（与 RoutingRuleService.buildGatewayTestPath 同源）；
  // 别名归一同样由后端能力接口的 canonicalCode 提供（原本地同名薄包装已并入调用点）
  return gatewayPathOf(canonicalOf(protocol)) || "";
}

/** 当前选中目标（或「全部目标」并集）对应的协议列表 */
function currentProtocolCodes(): string[] {
  if (testForm.targetKey === ALL_TARGETS_KEY) {
    return Array.from(new Set(Object.values(targetProtocolMap).flat() as string[]));
  }
  return (targetProtocolMap[testForm.targetKey] as string[]) || [];
}

/** 按当前协议列表校正已选协议并重载模板（切换目标后调用，协议已在打开抽屉时预加载） */
function syncCurrentProtocol() {
  const protocols = currentProtocolCodes();
  const normalized = canonicalOf(testForm.protocol);
  testForm.protocol = protocols.includes(normalized) ? normalized : protocols[0] || "";
  ensureTemplateForCurrentSelection();
}

/** 目标切换回调：协议已预加载，只需同步选中协议 */
function onTargetChange() {
  syncCurrentProtocol();
}

/**
 * 抽屉打开时载入各目标的协议（并集与单目标切换共用同一份缓存）。
 * 协议由后端 GET /api/routing-rules/{id}/debug-protocols 权威下发，与调试准入校验同源，
 * 前端不再解析模型/模型池的协议绑定，避免两份实现漂移。
 */
async function loadAllTargetProtocols() {
  protocolLoading.value = true;
  try {
    clearProtocolMap();
    if (props.ruleId == null) {
      syncCurrentProtocol();
      return;
    }
    const detail = await listRoutingRuleDebugProtocols(props.ruleId);
    for (const item of detail?.targets || []) {
      if (item?.targetId == null) {
        continue;
      }
      targetProtocolMap[`${normalizeTargetType(item.targetType)}:${item.targetId}`] = item.protocols || [];
    }
    syncCurrentProtocol();
  } catch {
    // 载入失败保持空表：协议下拉随之置空，不做本地兜底，避免下拉出后端实际不放行的协议
    clearProtocolMap();
    syncCurrentProtocol();
  } finally {
    protocolLoading.value = false;
  }
}

function clearProtocolMap() {
  for (const key of Object.keys(targetProtocolMap)) {
    delete targetProtocolMap[key];
  }
}

function ensureTemplateForCurrentSelection() {
  const protocol = activeProtocol.value;
  if (!protocol || !activeTemplateKey.value) {
    return;
  }
  if (!templateStore[activeTemplateKey.value]) {
    // 首次为该 (target, protocol) 生成模板：有可选项则默认选中第一个预设并载入其请求体
    seedTemplateForProtocol();
  } else if (selectedPresetId.value == null && filteredPresetOptions.value.length) {
    // 模板已存在（多为预设未加载时的空白占位）：补选第一个预设
    autoSelectFirstPreset();
  }
}

/** 为该 (target, protocol) 首次生成模板：存在可选项时默认选中第一个预设并载入请求体，否则留空白默认 */
function seedTemplateForProtocol() {
  const first = filteredPresetOptions.value[0];
  if (first) {
    onPresetChange(first.id);
  } else {
    selectedPresetId.value = null;
    templateStore[activeTemplateKey.value] = createDefaultTemplate(activeProtocol.value);
  }
}

/**
 * 预设请求下拉「默认选中第一个」：未选中且存在可选项时，自动选中过滤后的第一个。
 * 仅当模板仍为空白占位（未加载过预设 / 用户尚未编辑）才覆盖模板，避免清掉用户已输入的内容。
 */
function autoSelectFirstPreset() {
  if (selectedPresetId.value != null) {
    return;
  }
  const first = filteredPresetOptions.value[0];
  if (!first) {
    return;
  }
  const current = templateStore[activeTemplateKey.value] || "";
  const isBlank = !current.trim() || current.trim() === "{}";
  if (isBlank) {
    onPresetChange(first.id);
  } else {
    selectedPresetId.value = first.id;
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
    const page = await listTestRequestPresetOptions();
    // 统一 OptionPage 契约：value=id，label=name，接口协议走 meta.protocolCode；
    // requestBody 选中时按 id 懒加载
    presetOptions.value = (page?.list ?? []).map((item) => ({
      id: Number(item.value),
      name: item.label,
      protocolCode: item.meta?.protocolCode ?? ""
    }));
  } catch {
    presetOptions.value = [];
  }
  // 预设加载完成后，若当前未选中任何预设，默认选中过滤后的第一个（协议已确定时生效）
  autoSelectFirstPreset();
}

async function onPresetChange(presetId: number | string | null) {
  // 同步选中态：用户交互由 v-model 赋值，但程序化调用需在此显式设置
  selectedPresetId.value = presetId;
  if (!presetId) {
    return;
  }
  const preset = presetOptions.value.find((item) => String(item.id) === String(presetId));
  if (!preset) {
    return;
  }
  // options 契约不承载 requestBody（大字段），选中后按 id 懒加载预设详情
  let requestBody = "{}";
  try {
    const detail = await getTestRequestPreset(preset.id);
    if (String(selectedPresetId.value) !== String(presetId)) {
      return;
    }
    requestBody = detail?.requestBody || "{}";
  } catch {
    requestBody = "{}";
  }
  const parsed = safeParseTemplate(requestBody);
  const base = parsed.error ? {} : parsed.value;
  templateStore[activeTemplateKey.value] = formatJson(stripModelField(base));
}

function createDefaultTemplate(protocol: unknown): string {
  // 无预设可用时的空白默认请求体；具体结构由「预设请求」用例提供（替代原硬编码骨架）
  void protocol;
  return "{}";
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
  const json = parsedTemplateBody.value.error ? currentTemplateText.value || "{}" : stringifyJson(parsedTemplateBody.value.value || {});
  const escapedJson = json.replace(/'/g, "'\\''");
  // 请求头与「Headers」表格同源：用户在表格里的增删改即时反映到 curl 预览
  const headerLines = Object.entries(buildRequestHeaders("application/json"))
    .map(([name, value]) => `  -H '${name}: ${value.replace(/'/g, "'\\''")}'`)
    .join(" \\\n");
  return (
    `curl -X POST '${requestUrl.value}' \\\n` +
    `${headerLines} \\\n` +
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
  // 新测试一律清空上一轮裁判区：校验失败 / JSON 非法等早退分支同样不留残影
  resetVerdictState();
  if (!testForm.secretKey?.trim()) {
    validationError("请填写 API Key");
    return;
  }
  // 「全部目标」是合法选择（协议取并集，由网关按规则路由），无需具体目标
  if (testForm.targetKey !== ALL_TARGETS_KEY && !activeTarget.value) {
    validationError("请选择调试目标");
    return;
  }
  if (!activeProtocol.value) {
    validationError("当前目标没有可调试的接口协议");
    return;
  }
  const headerError = validateHeaderRows();
  if (headerError) {
    validationError(headerError);
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
  // 「全部目标」下 target 为 null（由网关按规则实际路由），结果里不展示 modelCode
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
async function runBufferedTest(requestBody: Dict, target: RoutingTarget | null, protocol: string) {
  loading.value = true;
  testResult.value = null;
  responseHeaders.value = {};
  resetVerdictState();
  // 清掉上一轮流式拼装内容，避免非流式概览复用残影
  streamContent.value = "";
  streamReasoning.value = "";
  try {
    const response = await testRoutingRule(
      requestUrl.value,
      stripModelField(requestBody),
      buildRequestHeaders("application/json")
    );
    responseHeaders.value = response.headers || {};
    // 被告提取：整包响应体顶层 knot_extend{usage, billing} + 并列 usage（上游原文）
    captureKnotExtend(response.data);
    testResult.value = {
      status: "SUCCESS",
      httpStatus: response.status,
      // 「全部目标」下网关按规则实际路由，命中模型取响应头 X-Knot-Model
      modelCode: target?.targetCode || servingModelCode(),
      protocol,
      curl: curlPreview.value,
      errorMessage: "",
      responseBody: serializeBody(response.data)
    };
    responseTab.value = "body";
    // 裁判第二步：不阻塞上方测试结果的渲染；开关关闭时跳过校验
    if (testBillingVerify.value) {
      void triggerBillingVerdict(stripModelField(requestBody), false);
    }
  } catch (error) {
    responseTab.value = "body";
    const err = error as Dict;
    if (err?.response?.headers) {
      responseHeaders.value = err.response.headers as Record<string, string>;
    }
    testResult.value = normalizeErrorResult(err) as RoutingTestResult;
  } finally {
    loading.value = false;
  }
}

/** 流式：SSE 增量展示，meta/chunk/complete/error 分状态推进 */
async function runStreamingTest(requestBody: Dict, target: RoutingTarget | null, protocol: string) {
  // 重复点击时先中止上一条，避免两个流同时写同一个缓冲区
  abortStreaming();
  const controller = new AbortController();
  streamAbortController.value = controller;
  streamStartedAt.value = Date.now();
  receivedBytes.value = 0;
  streamBody.value = "";
  responseHeaders.value = {};
  resetVerdictState();
  pendingText = "";
  streamBlocks.value = [];
  pendingBlocks = [];
  blockSeq = 0;
  streamContent.value = "";
  streamReasoning.value = "";
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
    const stream = await testRoutingRuleStream(
      requestUrl.value,
      gatewayBody,
      buildRequestHeaders("text/event-stream"),
      controller.signal
    );
    // 采集网关原生响应头原值（SSE 首包即带），供「响应 Header」tab 展示
    responseHeaders.value = stream.headers;
    if (testResult.value) {
      testResult.value = {
        ...testResult.value,
        httpStatus: stream.status,
        // 「全部目标」下网关按规则实际路由，命中模型取响应头 X-Knot-Model
        modelCode: target?.targetCode || servingModelCode()
      };
    }

    await stream.read(handleStreamEvent, {abortSignal: controller.signal});
    flushStreamText();
    // EOF 可能发生在区块节流定时器触发前，必须先刷入最后一批区块及概览内容。
    flushBlocks();
    recoverKnotExtendFromStreamBlocks();
    // 直连网关读取的是原生模型 SSE，没有管理端包装的 complete 事件；EOF 即成功结束。
    if (testResult.value?.status === "RUNNING") {
      finishStream("SUCCESS", "");
    }
    // 裁判第二步（v7 §3.2）：流式下 knot_extend 为 [DONE] 前独立事件，EOF 时已到手
    if (testBillingVerify.value) {
      if (knotExtend.value) {
        void triggerBillingVerdict(gatewayBody, true);
      } else if (testResult.value?.status === "SUCCESS") {
        verdictSkipped.value = true;
      }
    }
  } catch (error) {
    flushStreamText();
    // 网络错误或用户中止时也要保留已经收到但尚未节流刷新的内容。
    flushBlocks();
    if (isAbortError(error)) {
      // 用户主动停止：保留已收到内容，不弹全局网络错误
      if (testResult.value?.status === "RUNNING") {
        finishStream("STOPPED", "");
      }
      // 停止前若 knot_extend 已到手（[DONE] 已过）仍可判；否则无被告，不展示裁判区
      if (testBillingVerify.value && knotExtend.value) {
        void triggerBillingVerdict(stripModelField(requestBody), true);
      }
    } else {
      responseTab.value = "body";
      testResult.value = normalizeErrorResult(error as Dict) as RoutingTestResult;
      appendStreamError(testResult.value);
    }
  } finally {
    // 兜底处理所有结束路径，避免 finally 清理掉最后一批待刷数据。
    flushStreamText();
    flushBlocks();
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
  // 每个事件独立成块追加到「流式响应」页；同时从 data 反解出 content / reasoning_content 累加，供概览页展示完整内容。
  const dataText = event.data ?? "";
  const parsed = parseJsonResult(dataText, null);
  if (event.event === "error" && testResult.value) {
    const errorData = parsed.value && typeof parsed.value === "object" ? parsed.value as Dict : {};
    const errorMessage = String(errorData.errorMessage || errorData.message || "网关请求失败");
    testResult.value = {
      ...testResult.value,
      status: "ERROR",
      httpStatus: errorData.httpStatus ?? testResult.value.httpStatus,
      errorMessage,
      responseBody: String(errorData.responseBody || dataText)
    };
  }
  if (!parsed.error && parsed.value && typeof parsed.value === "object") {
    // knot_extend 事件（[DONE] 前网关注入的独立 SSE 事件）：提取被告负载，供裁判第二步使用
    pendingContent += extractField(parsed.value, "content");
    pendingReasoning += extractField(parsed.value, "reasoning_content");
  }
  captureKnotExtendFromStreamData(dataText);
  pendingBlocks.push({id: blockSeq++, event: event.event, data: dataText});
  scheduleBlockFlush();
  appendStreamText(formatUpstreamEvent(event));
}

/** SSE 区块是实际收到内容的留档；流结束时再核对一次，避免事件状态未及时进入裁判输入。 */
function recoverKnotExtendFromStreamBlocks() {
  if (knotExtend.value) {
    return;
  }
  for (const block of streamBlocks.value) {
    captureKnotExtendFromStreamData(block.data);
    if (knotExtend.value) {
      return;
    }
  }
}

/**
 * 从 content 类字段值提取文本：字符串原样返回；块数组（Anthropic content blocks /
 * Responses API output_text 等形态）拼接各块 text。
 */
function extractTextValue(value: unknown): string {
  if (typeof value === "string") {
    return value;
  }
  if (Array.isArray(value)) {
    return value
      .map((block) => {
        if (typeof block === "string") {
          return block;
        }
        if (block && typeof block === "object") {
          const text = (block as Dict).text;
          return typeof text === "string" ? text : "";
        }
        return "";
      })
      .join("");
  }
  return "";
}

/**
 * 从上游响应（单个 SSE data 或完整响应体）深搜指定字段并拼接：
 * 兼容 chat 流式 `choices[].delta.content`、缓冲 `choices[].message.content`、
 * completions `choices[].text`、Responses/Anthropic 的 content 块数组及顶层 `content`。
 * 仅取文本值，避免误抓无关同名字段造成的噪音（这里 content / reasoning_content 语义稳定）。
 */
function extractField(node: unknown, key: string): string {
  if (node == null || typeof node !== "object") {
    return "";
  }
  const results: string[] = [];
  const visit = (current: unknown) => {
    if (current == null || typeof current !== "object") {
      return;
    }
    if (Array.isArray(current)) {
      current.forEach(visit);
      return;
    }
    for (const [k, v] of Object.entries(current)) {
      if (k === key) {
        const text = extractTextValue(v);
        if (text) {
          results.push(text);
        } else if (v && typeof v === "object" && !Array.isArray(v)) {
          // 对象形态的 content（少数厂商）：继续下钻找同名字段
          visit(v);
        }
      } else if (v && typeof v === "object") {
        visit(v);
      }
    }
  };
  visit(node);
  return results.join("");
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
  if (pendingContent) {
    streamContent.value += pendingContent;
    pendingContent = "";
  }
  if (pendingReasoning) {
    streamReasoning.value += pendingReasoning;
    pendingReasoning = "";
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
  pendingContent = "";
  pendingReasoning = "";
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
    errorMessage: normalizeNetworkErrorMessage(error?.message),
    // fetch 网络失败没有 response body；仍把错误写入 Body，避免调试面板看起来像空响应。
    responseBody: normalizeNetworkErrorMessage(error?.message)
  };
}

/** 将 fetch 无响应错误转换成用户可操作的提示，避免只显示浏览器的英文 TypeError。 */
function normalizeNetworkErrorMessage(message: unknown): string {
  const raw = String(message || "").trim();
  if (!raw || /failed to fetch|networkerror|load failed/i.test(raw)) {
    return "无法连接 gateway 服务，请确认 gateway 已启动且地址可访问";
  }
  return `网络错误：${raw}`;
}

/** 流式请求在首个事件前失败时，同时填充 Body 和「流式响应」页。 */
function appendStreamError(result: RoutingTestResult) {
  const errorText = result.responseBody || result.errorMessage || "请求失败";
  const errorData = stringifyJson({
    status: result.status,
    httpStatus: result.httpStatus,
    errorMessage: result.errorMessage,
    responseBody: errorText
  });
  streamBlocks.value = [
    ...streamBlocks.value,
    {id: blockSeq++, event: "error", data: errorData}
  ];
  // 保留已收到的上游片段，并把本次错误追加到 Body，便于复制完整诊断信息。
  streamBody.value = streamBody.value
    ? `${streamBody.value}\n\n[error] ${errorText}`
    : errorText;
  result.responseBody = streamBody.value;
  responseTab.value = "stream";
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
  if (copyResetTimer) {
    clearTimeout(copyResetTimer);
    copyResetTimer = null;
  }
  copiedBlockId.value = null;
  if (elapsedTimer) {
    clearInterval(elapsedTimer);
    elapsedTimer = null;
  }
  pendingText = "";
  streamBody.value = "";
  streamBlocks.value = [];
  streamContent.value = "";
  streamReasoning.value = "";
  receivedBytes.value = 0;
  streamStartedAt.value = null;
  testResult.value = null;
  responseHeaders.value = {};
  resetVerdictState();
  headerRows.value = [];
  testBillingVerify.value = true;
  loading.value = false;
}

onBeforeUnmount(() => {
  abortStreaming();
  if (flushTimer) {
    clearTimeout(flushTimer);
    flushTimer = null;
  }
  clearBlockTimers();
  if (copyResetTimer) {
    clearTimeout(copyResetTimer);
    copyResetTimer = null;
  }
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
  font-size: var(--el-font-size-base, 13px);
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

/* 顶部目标/协议选择下拉框：各占整体宽度的 15%，与 URL、发送按钮并列 */
.request-toolbar__target {
  flex: 0 0 15%;
  min-width: 0;
}

.request-toolbar__protocol {
  flex: 0 0 15%;
  min-width: 0;
}

.request-toolbar__url {
  flex: 1 1 auto;
  min-width: 0;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 0;
  background: var(--el-fill-color-extra-light);
  color: var(--el-text-color-regular);
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 34px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.test-form {
  margin-bottom: 0;
}

/* 执行计费规则校验勾选行：始终显示 */
.test-billing-verify-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.test-billing-verify-toggle__hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.test-billing-verify-toggle__tag {
  margin-left: 4px;
  flex-shrink: 0;
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

/* 请求头表格：工具栏（说明 + 新增按钮）与表格本体 */
.header-table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.header-table-toolbar__hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.header-table {
  width: 100%;
}

/* 表内文本框边框统一为浅灰 #f6fffe（默认/悬浮态一致），聚焦仍保留主题色反馈 */
.header-table :deep(.el-input__wrapper),
.header-table :deep(.el-input__wrapper):hover {
  box-shadow: 0 0 0 1px #f6fffe inset;
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

/* 概览 tab 内 content / reasoning_content 作为同款卡片的值区：等宽、可滚动、限高 */
.result-meta__value--block {
  font-family: Consolas, "Courier New", monospace;
  font-weight: 400;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  max-height: 320px;
  overflow: auto;
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
  position: relative;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  overflow: hidden;
  background: #1e1e1e;
}

/* 头部行内复制按钮：margin-left:auto 靠右，flex 垂直居中与 #序号/event 文本对齐 */
/* 图标按钮：定宽定高 + 零内边距，避免文本按钮内边距把图标挤成胶囊 */
.stream-block__copy {
  margin-left: auto;
  width: 24px;
  min-width: 24px;
  height: 24px;
  min-height: 24px;
  padding: 0;
  background: #f5f7fa;
  color: #2563eb;
}

.stream-block__copy:hover,
.stream-block__copy:focus-visible {
  background: #dbeafe;
  color: #1d4ed8;
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
  .request-toolbar {
    flex-wrap: wrap;
  }

  .request-toolbar__target {
    flex: 0 0 100%;
  }

  .request-toolbar__protocol {
    flex: 0 0 100%;
  }

  .request-toolbar__url {
    flex: 1 1 100%;
  }

  .debug-panel--response {
    min-height: 420px;
  }
}
</style>
