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
            <el-tooltip v-if="!isStreaming" :content="secretKeyReady ? '发送请求' : '缺少消费者密钥，无法发送'" placement="top">
              <el-button
                type="primary"
                :icon="Promotion"
                :loading="loading"
                :disabled="!ruleId || !secretKeyReady"
                @click="runTest"
              />
            </el-tooltip>
            <el-tooltip v-else content="停止请求" placement="top">
              <el-button type="danger" :icon="CircleClose" @click="stopStreaming" />
            </el-tooltip>
          </div>

          <div v-if="!secretKeyReady" class="secret-missing-tip">
            <el-icon><WarningFilled/></el-icon>
            <span>{{ MISSING_SECRET_MESSAGE }}</span>
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

          <el-collapse v-model="expandedPanels" class="debug-collapse test-form">
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
import {CircleClose, CopyDocument, Delete, Plus, Promotion, Select, WarningFilled} from "@element-plus/icons-vue";
import JsonCodeEditor from "../common/JsonCodeEditor.vue";
import ShellCodeBlock from "../common/ShellCodeBlock.vue";
import RoutingBillingVerdictPanel from "./RoutingBillingVerdictPanel.vue";
import {
  listRoutingRuleDebugProtocols,
  testRoutingRule,
  testRoutingRuleBillingVerdict,
  testRoutingRuleStream
} from "@/api/routing";
import {useDebugCapabilities} from "@/composables/useDebugCapabilities";
import {useCopyToClipboard} from "@/composables/useCopyToClipboard";
import {useBillingVerdict} from "@/composables/useBillingVerdict";
import {useTestRequestComposition} from "@/composables/useTestRequestComposition";
import {useStreamBuffer, type StreamBlock, type RoutingTestResult} from "@/composables/useStreamBuffer";
import {getTestRequestPreset, listTestRequestPresetOptions} from "@/api/routing";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {parseJsonResult} from "@/utils/format";
import {
  extractField,
  highlightBlock,
  isAbortError,
  stripModelField
} from "@/utils/routingTestPayload";
import {
  normalizeErrorResult,
  serializeBody
} from "@/utils/routingTestError";
import {RouteTargetType, RoutingTestRunState, RoutingTestStatus} from "@/constants/status";
import type {Dict} from "@/types";

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

const testResult = ref<RoutingTestResult | null>(null);
/** 最近一次响应的响应头，供「响应 Header」tab 展示 */
const responseHeaders = ref<Record<string, string>>({});
/** 目标 key → 该目标可调试的协议 code；清空就是整体换一个空对象（比逐个 delete key 更省心） */
const targetProtocolMap = ref<Record<string, string[]>>({});

/** 区块滚动容器引用，用于追加后自动滚到底部 */
const streamScrollbar = ref<ScrollbarLike | null>(null);

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
  if (testResult.value?.status === RoutingTestRunState.RUNNING) {
    return "warning";
  }
  return testResult.value?.status === RoutingTestStatus.SUCCESS ? "success" : "danger";
});

const testForm = reactive({
  targetKey: "",
  protocol: ""
});

/**
 * 密钥缺失的唯一文案：工具栏提示与 `runTest` 的拦截面共用同一句，
 * 避免"界面说 A、报错说 B"（旧实现界面无提示、报错却说「请填写 API Key」）。
 */
const MISSING_SECRET_MESSAGE =
  "该规则未绑定消费者，或取不到消费者密钥，无法发起测试。请到「消费者」页确认后重新打开本抽屉。";

/**
 * 消费者密钥是否可用。
 *
 * <p>抽屉**不提供密钥录入**：测试用的是规则所绑消费者的密钥，由父组件按 `consumerCode` 取详情后传入。
 * 取不到（规则未绑消费者 / 接口失败）时父组件传空串——此时必须禁用发送并说明原因，
 * 否则用户会看到「请填写 API Key」却没有任何地方能填（曾经就是这条死路：校验拦住了，
 * 界面不给出口，提示还让人去"填写"）。</p>
 */
const secretKeyReady = computed(() => !!props.secretKey?.trim());

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
const availableProtocols = computed(() =>
  // 与 syncCurrentProtocol 同一口径：这里只负责加 label，协议集合只由 currentProtocolCodes 决定
  currentProtocolCodes().map((code) => ({code, label: protocolLabel(code)}))
);
const activeProtocol = computed(() => canonicalOf(testForm.protocol));

// 请求构造侧（协议路径 / URL / 请求头表格 / 模板 / 预设）全部由 composable 承载，
// 抽屉只把「当前选择」与两个接口注入进去。同 3.6 的边界：**只抽状态，不抽编排**——
// 发请求、判错误类型、触发裁判仍是本抽屉的职责。
const {
  requestUrl,
  headerRows,
  displayHeaderRows,
  resetHeaderRows,
  addHeaderRow,
  removeHeaderRow,
  normalizeHeaderRow,
  validateHeaderRows,
  buildRequestHeaders,
  currentTemplateText,
  parsedTemplateBody,
  ensureTemplateForCurrentSelection,
  resetCurrentTemplate,
  selectedPresetId,
  filteredPresetOptions,
  loadPresetOptions,
  onPresetChange,
  curlPreview,
  formatBody
} = useTestRequestComposition({
  secretKey: () => props.secretKey,
  ruleCode: () => props.ruleCode,
  targetKey: () => testForm.targetKey,
  protocol: activeProtocol,
  billingVerifyEnabled: testBillingVerify,
  consumerReturnUsageDetail: () => props.consumerReturnUsageDetail,
  canonicalOf,
  gatewayPathOf,
  listPresetOptions: listTestRequestPresetOptions,
  loadPresetDetail: getTestRequestPreset
});

// 计费裁判（JEV 双盲校验 v7 §3.2 第二步）：状态与动作全部由 composable 承载，
// 抽屉只负责把「当前选择」与裁判接口注入进去（接口注入而非直接 import，便于脱离 HTTP 单测）。
const {
  knotExtend,
  verdictResult,
  verdictLoading,
  verdictSkipped,
  verdictError,
  verdictActive,
  resetVerdictState,
  captureKnotExtend,
  captureKnotExtendFromStreamData,
  servingModelCode,
  triggerBillingVerdict
} = useBillingVerdict({
  ruleId: () => props.ruleId,
  targetCode: () => activeTarget.value?.targetCode,
  protocol: () => activeProtocol.value,
  responseHeaders,
  requestVerdict: testRoutingRuleBillingVerdict
});

/** 面板可见性：开关开启且任一裁判态激活即渲染（skipped / error 也是设计内的展示态） */
const verdictVisible = computed(() => testBillingVerify.value && verdictActive.value);

/** 区块追加后滚到底部：DOM 动作留在抽屉，以回调注入缓冲 composable（直接摸元素 ref 就不可测了） */
function scrollStreamToBottom() {
  nextTick(() => {
    const wrap = streamScrollbar.value?.wrapRef;
    if (wrap) {
      wrap.scrollTop = wrap.scrollHeight;
    }
  });
}

// 流式缓冲与节流状态机：只管「收到的字节怎么攒、何时刷进界面、怎么固化成终态」，
// 发请求 / 判错误类型 / 触发裁判仍是本抽屉的编排职责。
const {
  streamBody,
  receivedBytes,
  streamStartedAt,
  streamBlocks,
  streamContent,
  streamReasoning,
  elapsedTick,
  isStreaming,
  beginStream,
  endStream,
  handleStreamEvent,
  flushStreamText,
  flushBlocks,
  recoverKnotExtendFromStreamBlocks,
  finishStream,
  appendStreamError,
  abortStreaming,
  disposeStreamResources
} = useStreamBuffer({
  testResult,
  knotExtend,
  captureKnotExtendFromStreamData,
  onScrollToBottom: scrollStreamToBottom
});

/** 用户点击「停止请求」 */
function stopStreaming() {
  abortStreaming();
}

const protocolHint = computed(() => hintOf(activeProtocol.value));

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

/**
 * 从非流式响应体里按优先级取顶层字段（一次解析、多字段兜底）。
 * 流式阶段的值优先由调用方各自处理——两者的「流式优先」语义不同，不在这里合并。
 */
function fieldsFromResponseBody(names: string[]): string {
  const body = testResult.value?.responseBody;
  if (!body) {
    return "";
  }
  const parsed = parseJsonResult(body, null);
  if (parsed.error) {
    return "";
  }
  for (const name of names) {
    const found = extractField(parsed.value, name);
    if (found) {
      return found;
    }
  }
  return "";
}

/** 概览页「完整内容」：优先用流式拼装的 content，否则从非流式响应体反解 */
const displayContent = computed(() =>
  streamContent.value || fieldsFromResponseBody(["content", "text"])
);

/** 概览页「思考过程」：reasoning_content，与 content 分开 */
const displayReasoning = computed(() =>
  streamReasoning.value || fieldsFromResponseBody(["reasoning_content"])
);
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
    // 唯一的目标切换入口：模板不再挂 @change（两条路径会各跑一次 syncCurrentProtocol）
    syncCurrentProtocol();
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
  return String(targetType || RouteTargetType.MODEL).trim().toUpperCase();
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

/** 当前选中目标（或「全部目标」并集）对应的协议列表 */
function currentProtocolCodes(): string[] {
  // 「全部目标」= 所有目标协议并集；选定单一目标 = 该目标协议。多目标规则下各目标协议可能不同
  // （如主目标 CHAT_COMPLETIONS、次目标 RESPONSES），只取主目标会漏掉后者。
  if (testForm.targetKey === ALL_TARGETS_KEY) {
    return Array.from(new Set(Object.values(targetProtocolMap.value).flat()));
  }
  return targetProtocolMap.value[testForm.targetKey] || [];
}

/** 按当前协议列表校正已选协议并重载模板（切换目标后调用，协议已在打开抽屉时预加载） */
function syncCurrentProtocol() {
  const protocols = currentProtocolCodes();
  const normalized = canonicalOf(testForm.protocol);
  // 原协议不在新目标的可调试集合里就必须换掉：留着它会让下拉显示一个本次根本不允许的值，
  // 而 runTest 只校验「非空」，于是拿着无效协议发出去
  testForm.protocol = protocols.includes(normalized) ? normalized : protocols[0] || "";
  ensureTemplateForCurrentSelection();
}

/**
 * 抽屉打开时载入各目标的协议（并集与单目标切换共用同一份缓存）。
 * 协议由后端 GET /api/routing-rules/{id}/debug-protocols 权威下发，与调试准入校验同源，
 * 前端不再解析模型/模型池的协议绑定，避免两份实现漂移。
 */
async function loadAllTargetProtocols() {
  protocolLoading.value = true;
  try {
    targetProtocolMap.value = {};
    if (props.ruleId == null) {
      syncCurrentProtocol();
      return;
    }
    const detail = await listRoutingRuleDebugProtocols(props.ruleId);
    const next: Record<string, string[]> = {};
    for (const item of detail?.targets || []) {
      if (item?.targetId == null) {
        continue;
      }
      next[`${normalizeTargetType(item.targetType)}:${item.targetId}`] = item.protocols || [];
    }
    targetProtocolMap.value = next;
    syncCurrentProtocol();
  } catch {
    // 载入失败保持空表：协议下拉随之置空，不做本地兜底，避免下拉出后端实际不放行的协议
    targetProtocolMap.value = {};
    syncCurrentProtocol();
  } finally {
    protocolLoading.value = false;
  }
}

/** 收集请求前置校验失败，统一的提示形态（无返回值：4 处调用都是「设完就 return」） */
function validationError(message: string): void {
  responseTab.value = "summary";
  testResult.value = {
    status: RoutingTestRunState.ERROR,
    httpStatus: null,
    modelCode: null,
    protocol: activeProtocol.value || null,
    errorMessage: message,
    responseBody: ""
  };
}

async function runTest() {
  if (!props.ruleId) return;
  // 新测试一律清空上一轮裁判区：校验失败 / JSON 非法等早退分支同样不留残影
  resetVerdictState();
  if (!secretKeyReady.value) {
    validationError(MISSING_SECRET_MESSAGE);
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
      status: RoutingTestRunState.ERROR,
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
      status: RoutingTestStatus.SUCCESS,
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
    testResult.value = normalizeErrorResult(err, activeProtocol.value) as RoutingTestResult;
  } finally {
    loading.value = false;
  }
}

/** 流式：SSE 增量展示，meta/chunk/complete/error 分状态推进 */
async function runStreamingTest(requestBody: Dict, target: RoutingTarget | null, protocol: string) {
  // 重复点击时先中止上一条，避免两个流同时写同一个缓冲区
  abortStreaming();
  const controller = new AbortController();
  // 缓冲复位与「已用时」计时器由 composable 统一负责
  beginStream(controller);
  responseHeaders.value = {};
  resetVerdictState();
  loading.value = true;
  // 先切到「流式响应」页并建立 RUNNING 态，避免等待期间显示空 skeleton
  responseTab.value = "stream";
  testResult.value = {
    status: RoutingTestRunState.RUNNING,
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
    if (testResult.value?.status === RoutingTestRunState.RUNNING) {
      finishStream(RoutingTestStatus.SUCCESS, "");
    }
    // 裁判第二步（v7 §3.2）：流式下 knot_extend 为 [DONE] 前独立事件，EOF 时已到手
    if (testBillingVerify.value) {
      if (knotExtend.value) {
        void triggerBillingVerdict(gatewayBody, true);
      } else if (testResult.value?.status === RoutingTestStatus.SUCCESS) {
        verdictSkipped.value = true;
      }
    }
  } catch (error) {
    flushStreamText();
    // 网络错误或用户中止时也要保留已经收到但尚未节流刷新的内容。
    flushBlocks();
    if (isAbortError(error)) {
      // 用户主动停止：保留已收到内容，不弹全局网络错误
      if (testResult.value?.status === RoutingTestRunState.RUNNING) {
        finishStream(RoutingTestRunState.STOPPED, "");
      }
      // 停止前若 knot_extend 已到手（[DONE] 已过）仍可判；否则无被告，不展示裁判区
      if (testBillingVerify.value && knotExtend.value) {
        void triggerBillingVerdict(stripModelField(requestBody), true);
      }
    } else {
      responseTab.value = "body";
      testResult.value = normalizeErrorResult(error, activeProtocol.value) as RoutingTestResult;
      appendStreamError(testResult.value);
    }
  } finally {
    // 兜底处理所有结束路径：flush 掉最后一批待刷数据 + 释放三个计时器 + 结束流式态
    endStream();
    loading.value = false;
  }
}

/**
 * 释放调试占用的所有外部资源：在途流、四个计时器、待刷缓冲。
 *
 * <p><b>唯一清理入口</b>——关闭抽屉与组件卸载都必须走这里。此前两处各抄一遍，
 * 结果「新增计时器只改了一处」就会漏清理：在途请求不中止会一直占用网关连接，
 * 计时器不清会对着已销毁的组件写响应式状态。</p>
 *
 * <p>只做资源释放，不碰展示数据——数据复位是 {@link onClosed} 的职责，
 * 卸载路径不需要（也不应该）再动一次响应式状态。</p>
 */
function disposeTestResources() {
  // 关闭抽屉/卸载组件必须中止在途流，否则网关连接与异步线程一直被占用
  // 流式侧的四个资源（在途流 + 三个计时器 + 待刷缓冲）由 composable 统一释放
  disposeStreamResources();
  if (copyResetTimer) {
    clearTimeout(copyResetTimer);
    copyResetTimer = null;
  }
}

function onClosed() {
  disposeTestResources();
  copiedBlockId.value = null;
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

onBeforeUnmount(disposeTestResources);
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

/* 缺密钥警示条：发送按钮被禁用的原因必须写在界面上，不能让用户对着禁用按钮猜 */
.secret-missing-tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 10px;
  padding: 8px 10px;
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: 4px;
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning-dark-2);
  font-size: 12px;
  line-height: 1.5;
}

.secret-missing-tip .el-icon {
  margin-top: 2px;
  flex: 0 0 auto;
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
