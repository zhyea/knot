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
            <el-button type="primary" :loading="loading" :disabled="!ruleId" @click="runTest">
              发送请求
            </el-button>
          </div>

          <div class="request-summary">
            <el-tag size="small" effect="plain">规则：{{ props.ruleName?.trim() || `#${props.ruleId || "-"}` }}</el-tag>
            <el-tag size="small" effect="plain">目标：{{ activeTargetLabel }}</el-tag>
            <el-tag size="small" effect="plain">协议：{{ activeProtocolLabel }}</el-tag>
            <el-tag size="small" effect="plain">HTTP {{ testResult?.httpStatus ?? "-" }}</el-tag>
          </div>

          <el-form label-width="72px" class="test-form">
            <div class="test-form__row test-form__row--three">
              <el-form-item label="API Key" required>
                <el-input
                  v-model="testForm.secretKey"
                  placeholder="sk- 开头的消费者 API Key"
                  type="password"
                  show-password
                />
              </el-form-item>
              <el-form-item label="目标">
                <el-select
                  v-model="testForm.targetKey"
                  placeholder="选择调试目标"
                  filterable
                  style="width: 100%"
                  @change="onTargetChange"
                >
                  <el-option
                    v-for="target in targetOptions"
                    :key="target.key"
                    :label="target.label"
                    :value="target.key"
                  />
                </el-select>
              </el-form-item>
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

            <div class="request-template">
              <div class="request-template__head">
                <div>
                  <h4>请求模板</h4>
                  <p>每种协议维护独立模板，切换协议后自动保留。</p>
                </div>
                <el-button size="small" @click="resetCurrentTemplate">重置模板</el-button>
              </div>
              <JsonCodeEditor
                v-model="currentTemplateText"
                min-height="220px"
                max-height="320px"
              />
              <div v-if="protocolHint" class="request-template__hint">{{ protocolHint }}</div>
            </div>
          </el-form>

          <el-collapse v-model="expandedPanels" class="request-preview-collapse">
            <el-collapse-item title="请求预览" name="preview">
              <el-tabs v-model="requestTab" class="debug-tabs">
                <el-tab-pane label="Headers" name="headers">
                  <ShellCodeBlock :code="requestHeadersText" language="json" :copyable="true" />
                </el-tab-pane>
                <el-tab-pane label="Body" name="body">
                  <ShellCodeBlock :code="requestBodyText" language="json" :copyable="true" />
                </el-tab-pane>
                <el-tab-pane label="curl" name="curl">
                  <ShellCodeBlock :code="displayCurl" language="bash" :copyable="true" />
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
            <el-tag v-if="testResult" :type="testResult.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ testResult.status }}
              <template v-if="testResult.httpStatus != null"> · HTTP {{ testResult.httpStatus }}</template>
            </el-tag>
          </div>

          <el-skeleton v-if="loading" :rows="5" animated />
          <template v-else-if="testResult">
            <el-tabs v-model="responseTab" class="debug-tabs">
              <el-tab-pane label="概览" name="summary">
                <div v-if="summaryItems.length" class="result-meta">
                  <div v-for="item in summaryItems" :key="item.label" class="result-meta__item" :class="item.className">
                    <span class="result-meta__label">{{ item.label }}</span>
                    <span class="result-meta__value">{{ item.value }}</span>
                  </div>
                </div>
                <el-empty v-else description="无额外结果信息" :image-size="64" />
              </el-tab-pane>
              <el-tab-pane label="Body" name="body">
                <ShellCodeBlock
                  v-if="resultBodyText"
                  :code="resultBodyText"
                  language="json"
                  :copyable="true"
                />
                <el-empty v-else description="无响应内容" :image-size="64" />
              </el-tab-pane>
            </el-tabs>
          </template>
          <el-empty v-else description="执行请求后在这里查看响应结果" :image-size="72" />
        </section>
      </div>
    </el-scrollbar>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import JsonCodeEditor from "../common/JsonCodeEditor.vue";
import ShellCodeBlock from "../common/ShellCodeBlock.vue";
import { getModel } from "../../api/models";
import { getModelPool } from "../../api/modelPools";
import { testRoutingRule } from "../../api/routing";
import { formatJson, formatJsonText, parseJsonResult, stringifyJson } from "../../utils/format";

const GATEWAY_BASE_URL = import.meta.env.VITE_GATEWAY_BASE_URL || "http://127.0.0.1:9090";
const DEFAULT_PROMPT = "你好，这是一条路由规则测试消息";

const PROTOCOL_LABELS = {
  CHAT_COMPLETIONS: "Chat Completions",
  RESPONSES: "Responses",
  MESSAGES: "Messages",
  COMPLETIONS: "Completions",
  EMBEDDINGS: "Embeddings",
  IMAGE_GENERATIONS: "Image Generations",
  IMAGE_EDITS: "Image Edits",
  IMAGE_VARIATIONS: "Image Variations",
  AUDIO_TRANSCRIPTIONS: "Audio Transcriptions",
  AUDIO_TRANSLATIONS: "Audio Translations",
  AUDIO_SPEECH: "Audio Speech",
  VIDEO_GENERATIONS: "Video Generations",
  RERANK: "Rerank",
  MODERATIONS: "Moderations"
};

const PROTOCOL_HINTS = {
  CHAT_COMPLETIONS: "适用于标准对话请求，通常需要 messages。",
  RESPONSES: "适用于 OpenAI Responses 协议，通常使用 input。",
  MESSAGES: "适用于 Anthropic Messages 协议，通常需要 messages 和 max_tokens。",
  COMPLETIONS: "适用于传统文本补全协议，通常使用 prompt。",
  EMBEDDINGS: "适用于向量化请求，通常使用 input。",
  IMAGE_GENERATIONS: "适用于文生图，通常使用 prompt。",
  IMAGE_EDITS: "适用于图像编辑，通常需要 prompt 和 image。image 可先用 URL、data URL 或占位值维护模板。",
  IMAGE_VARIATIONS: "适用于图像变体生成，通常需要 image。",
  AUDIO_TRANSCRIPTIONS: "适用于语音转录，通常需要 file。",
  AUDIO_TRANSLATIONS: "适用于语音翻译，通常需要 file。",
  AUDIO_SPEECH: "适用于语音合成，通常使用 input 和 voice。",
  VIDEO_GENERATIONS: "适用于视频生成，通常使用 prompt。",
  RERANK: "适用于重排序，通常使用 query 和 documents。",
  MODERATIONS: "适用于内容安全审核，通常使用 input。"
};

const PROTOCOL_PATHS = {
  CHAT_COMPLETIONS: "/openai/v1/chat/completions",
  RESPONSES: "/openai/v1/responses",
  MESSAGES: "/anthropic/v1/messages",
  COMPLETIONS: "/openai/v1/completions",
  EMBEDDINGS: "/openai/v1/embeddings",
  IMAGE_GENERATIONS: "/openai/v1/images/generations",
  IMAGE_EDITS: "/openai/v1/images/edits",
  IMAGE_VARIATIONS: "/openai/v1/images/variations",
  AUDIO_TRANSCRIPTIONS: "/openai/v1/audio/transcriptions",
  AUDIO_TRANSLATIONS: "/openai/v1/audio/translations",
  AUDIO_SPEECH: "/openai/v1/audio/speech",
  VIDEO_GENERATIONS: "/openai/v1/videos/generations",
  RERANK: "/v1/rerank",
  MODERATIONS: "/openai/v1/moderations"
};

const MODEL_TYPE_FALLBACK_PROTOCOLS = {
  CHAT: ["CHAT_COMPLETIONS", "RESPONSES", "MESSAGES", "COMPLETIONS"],
  TEXT: ["CHAT_COMPLETIONS", "RESPONSES", "MESSAGES", "COMPLETIONS"],
  REASONING: ["CHAT_COMPLETIONS", "RESPONSES", "MESSAGES", "COMPLETIONS"],
  MULTIMODAL: [
    "CHAT_COMPLETIONS",
    "RESPONSES",
    "MESSAGES",
    "COMPLETIONS",
    "IMAGE_GENERATIONS",
    "IMAGE_EDITS",
    "IMAGE_VARIATIONS",
    "AUDIO_TRANSCRIPTIONS",
    "AUDIO_TRANSLATIONS",
    "AUDIO_SPEECH",
    "VIDEO_GENERATIONS"
  ],
  EMBEDDING: ["EMBEDDINGS"],
  IMAGE: ["IMAGE_GENERATIONS", "IMAGE_EDITS", "IMAGE_VARIATIONS"],
  AUDIO: ["AUDIO_TRANSCRIPTIONS", "AUDIO_TRANSLATIONS", "AUDIO_SPEECH"],
  VIDEO: ["VIDEO_GENERATIONS"],
  RERANK: ["RERANK"],
  DOCUMENT: ["CHAT_COMPLETIONS", "RESPONSES", "MESSAGES"],
  OCR: ["CHAT_COMPLETIONS", "RESPONSES", "MESSAGES"],
  MODERATION: ["MODERATIONS"],
  UTILITY: ["RERANK", "MODERATIONS"]
};

const PROTOCOL_CANONICAL_MAP = {
  OPENAI_CHAT_COMPLETIONS: "CHAT_COMPLETIONS",
  OPENAI_RESPONSES: "RESPONSES",
  ANTHROPIC_MESSAGES: "MESSAGES",
  OPENAI_COMPLETIONS: "COMPLETIONS"
};

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  ruleId: { type: Number, default: null },
  ruleName: { type: String, default: "" },
  secretKey: { type: String, default: "" },
  targets: { type: Array, default: () => [] }
});

const emit = defineEmits(["update:modelValue"]);

const loading = ref(false);
const protocolLoading = ref(false);
const requestTab = ref("body");
const responseTab = ref("summary");
const expandedPanels = ref([]);
const testResult = ref(null);
const targetProtocolMap = reactive({});
const targetResolvedModelMap = reactive({});
const templateStore = reactive({});

const testForm = reactive({
  secretKey: "",
  targetKey: "",
  protocol: ""
});

const drawerTitle = computed(() => {
  const name = props.ruleName?.trim();
  return name ? `路由规则测试 — ${name}` : "路由规则测试";
});

const targetOptions = computed(() => {
  const list = Array.isArray(props.targets) ? props.targets : [];
  return list
    .filter((item) => item?.targetId != null)
    .map((item) => ({
      ...item,
      key: targetKeyOf(item),
      label: targetLabel(item)
    }));
});

const activeTarget = computed(() => targetOptions.value.find((item) => item.key === testForm.targetKey) || null);
const availableProtocols = computed(() => {
  const protocols = targetProtocolMap[testForm.targetKey] || [];
  return protocols.map((code) => ({ code, label: protocolLabel(code) }));
});
const activeProtocol = computed(() => normalizeProtocolCode(testForm.protocol));
const activeTargetLabel = computed(() => activeTarget.value?.label || "-");
const activeProtocolLabel = computed(() => protocolLabel(activeProtocol.value));
const activeTemplateKey = computed(() => `${testForm.targetKey || "default"}::${activeProtocol.value || "default"}`);
const protocolHint = computed(() => PROTOCOL_HINTS[activeProtocol.value] || "");
const resolvedModel = computed(() => {
  const targetKey = testForm.targetKey;
  return targetResolvedModelMap[targetKey] || activeTarget.value?.targetCode || "model-name";
});

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
const requestBodyText = computed(() => {
  if (parsedTemplateBody.value.error) {
    return currentTemplateText.value || "{}";
  }
  return formatJson(parsedTemplateBody.value.value || {});
});
const curlPreview = computed(() => buildCurlCommand());
const displayCurl = computed(() => testResult.value?.curl || curlPreview.value);
const resultBodyText = computed(() => {
  if (!testResult.value?.responseBody) {
    return "";
  }
  return formatBody(testResult.value.responseBody);
});
const summaryItems = computed(() => {
  if (!testResult.value) {
    return [];
  }
  const items = [];
  if (testResult.value.protocol) {
    items.push({ label: "接口协议", value: protocolLabel(testResult.value.protocol) });
  }
  if (testResult.value.modelCode) {
    items.push({ label: "命中模型", value: testResult.value.modelCode });
  }
  if (testResult.value.errorMessage) {
    items.push({ label: "错误信息", value: testResult.value.errorMessage, className: "result-meta__item--error" });
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
    requestTab.value = "body";
    responseTab.value = "summary";
    expandedPanels.value = [];
    testForm.secretKey = props.secretKey || "";
    initializeTargetSelection();
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

function targetKeyOf(target) {
  return `${normalizeTargetType(target.targetType)}:${target.targetId}`;
}

function normalizeTargetType(targetType) {
  return String(targetType || "MODEL").trim().toUpperCase();
}

function targetLabel(target) {
  const type = normalizeTargetType(target.targetType) === "MODEL_POOL" ? "模型池" : "模型";
  const name = target.targetName || target.targetCode || `#${target.targetId}`;
  return target.primary ? `${type}：${name}（主）` : `${type}：${name}`;
}

function protocolLabel(protocol) {
  const code = normalizeProtocolCode(protocol);
  return PROTOCOL_LABELS[code] || code || "-";
}

function protocolPath(protocol) {
  return PROTOCOL_PATHS[normalizeProtocolCode(protocol)] || "/openai/v1/chat/completions";
}

function normalizeProtocolCode(protocol) {
  const code = String(protocol || "").trim().toUpperCase();
  return PROTOCOL_CANONICAL_MAP[code] || code;
}

function fallbackProtocolsForModelType(modelType) {
  const type = String(modelType || "CHAT").trim().toUpperCase();
  return MODEL_TYPE_FALLBACK_PROTOCOLS[type] || MODEL_TYPE_FALLBACK_PROTOCOLS.CHAT;
}

async function onTargetChange() {
  await loadProtocolsForCurrentTarget();
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
    const protocols = detail.protocols.length ? detail.protocols : fallbackProtocolsForModelType(target.modelType);
    targetProtocolMap[target.key] = protocols;
    if (detail.resolvedModel) {
      targetResolvedModelMap[target.key] = detail.resolvedModel;
    } else if (!targetResolvedModelMap[target.key]) {
      targetResolvedModelMap[target.key] = target.targetCode || "model-name";
    }
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

async function resolveTargetProtocolDetail(target) {
  if (normalizeTargetType(target.targetType) === "MODEL_POOL") {
    return await loadModelPoolProtocols(target);
  }
  return await loadModelProtocols(target.targetId, target.modelType, target.targetCode);
}

async function loadModelProtocols(modelId, modelType, fallbackModelCode) {
  try {
    const detail = await getModel(modelId);
    const bindings = Array.isArray(detail?.apiBindings) ? detail.apiBindings : [];
    const protocols = normalizeProtocolsFromBindings(bindings, modelType);
    return {
      protocols,
      resolvedModel: detail?.modelCode || fallbackModelCode || "model-name"
    };
  } catch {
    return {
      protocols: fallbackProtocolsForModelType(modelType),
      resolvedModel: fallbackModelCode || "model-name"
    };
  }
}

async function loadModelPoolProtocols(target) {
  try {
    const detail = await getModelPool(target.targetId);
    const enabledItems = (Array.isArray(detail?.items) ? detail.items : []).filter((item) => item.enabled !== false);
    if (!enabledItems.length) {
      return {
        protocols: fallbackProtocolsForModelType(target.modelType),
        resolvedModel: target.targetCode || "model-name"
      };
    }
    const models = await Promise.all(enabledItems.map((item) => loadModelProtocols(item.modelId, item.modelType, item.modelCode)));
    const protocolLists = models.map((item) => item.protocols).filter((item) => item.length);
    const protocols = intersectProtocolLists(protocolLists);
    const resolvedModel = selectPoolResolvedModel(enabledItems);
    return {
      protocols: protocols.length ? protocols : fallbackProtocolsForModelType(target.modelType),
      resolvedModel: resolvedModel || target.targetCode || "model-name"
    };
  } catch {
    return {
      protocols: fallbackProtocolsForModelType(target.modelType),
      resolvedModel: target.targetCode || "model-name"
    };
  }
}

function selectPoolResolvedModel(items) {
  const sorted = [...items].sort((left, right) => {
    const priorityDiff = (right.priority ?? 100) - (left.priority ?? 100);
    if (priorityDiff !== 0) return priorityDiff;
    const weightDiff = (right.weight ?? 100) - (left.weight ?? 100);
    if (weightDiff !== 0) return weightDiff;
    return (left.id ?? 0) - (right.id ?? 0);
  });
  return sorted[0]?.modelCode || "";
}

function normalizeProtocolsFromBindings(bindings, modelType) {
  const protocols = bindings
    .filter((item) => item?.enabled !== false)
    .map((item) => normalizeProtocolCode(item.protocol))
    .filter((item) => Boolean(PROTOCOL_PATHS[item]));
  return protocols.length ? Array.from(new Set(protocols)) : fallbackProtocolsForModelType(modelType);
}

function intersectProtocolLists(lists) {
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
  if (!templateStore[activeTemplateKey.value]) {
    templateStore[activeTemplateKey.value] = createDefaultTemplate(protocol, resolvedModel.value);
    return;
  }
  syncTemplateModelField(activeTemplateKey.value, resolvedModel.value);
}

function resetCurrentTemplate() {
  if (!activeProtocol.value) {
    return;
  }
  templateStore[activeTemplateKey.value] = createDefaultTemplate(activeProtocol.value, resolvedModel.value);
}

function createDefaultTemplate(protocol, model) {
  const body = buildDefaultTemplateObject(protocol, model || "model-name");
  return formatJson(body);
}

function buildDefaultTemplateObject(protocol, model) {
  switch (normalizeProtocolCode(protocol)) {
    case "CHAT_COMPLETIONS":
      return {
        model,
        messages: [{ role: "user", content: DEFAULT_PROMPT }]
      };
    case "RESPONSES":
      return {
        model,
        input: DEFAULT_PROMPT
      };
    case "MESSAGES":
      return {
        model,
        max_tokens: 1024,
        messages: [{ role: "user", content: DEFAULT_PROMPT }]
      };
    case "COMPLETIONS":
      return {
        model,
        prompt: DEFAULT_PROMPT
      };
    case "EMBEDDINGS":
      return {
        model,
        input: [DEFAULT_PROMPT]
      };
    case "IMAGE_GENERATIONS":
      return {
        model,
        prompt: DEFAULT_PROMPT
      };
    case "IMAGE_EDITS":
      return {
        model,
        prompt: DEFAULT_PROMPT,
        image: "https://example.com/image.png"
      };
    case "IMAGE_VARIATIONS":
      return {
        model,
        image: "https://example.com/image.png"
      };
    case "AUDIO_TRANSCRIPTIONS":
      return {
        model,
        file: "D:/path/to/audio.mp3"
      };
    case "AUDIO_TRANSLATIONS":
      return {
        model,
        file: "D:/path/to/audio.mp3"
      };
    case "AUDIO_SPEECH":
      return {
        model,
        input: DEFAULT_PROMPT,
        voice: "alloy"
      };
    case "VIDEO_GENERATIONS":
      return {
        model,
        prompt: DEFAULT_PROMPT
      };
    case "RERANK":
      return {
        model,
        query: DEFAULT_PROMPT,
        documents: ["文档 1", "文档 2"],
        top_n: 2
      };
    case "MODERATIONS":
      return {
        model,
        input: DEFAULT_PROMPT
      };
    default:
      return { model };
  }
}

function syncTemplateModelField(templateKey, model) {
  const parsed = safeParseTemplate(templateStore[templateKey]);
  if (parsed.error || !parsed.value || typeof parsed.value !== "object" || Array.isArray(parsed.value)) {
    return;
  }
  const next = { ...parsed.value, model: model || parsed.value.model || "model-name" };
  templateStore[templateKey] = formatJson(next);
}

function safeParseTemplate(text) {
  return parseJsonResult(text, {});
}

function normalizeBaseUrl() {
  const base = GATEWAY_BASE_URL.trim();
  return base.endsWith("/") ? base.slice(0, -1) : base;
}

function resolveRuleHeaderValue() {
  return props.ruleName?.trim() || props.ruleId || "your-rule-code";
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

function formatBody(body) {
  if (!body) return "";
  return formatJsonText(body, 2, body);
}

async function runTest() {
  if (!props.ruleId) return;
  if (!testForm.secretKey?.trim()) {
    responseTab.value = "summary";
    testResult.value = {
      status: "ERROR",
      httpStatus: null,
      modelCode: null,
      protocol: activeProtocol.value || null,
      errorMessage: "请填写 API Key",
      responseBody: ""
    };
    return;
  }
  if (!activeTarget.value) {
    responseTab.value = "summary";
    testResult.value = {
      status: "ERROR",
      httpStatus: null,
      modelCode: null,
      protocol: activeProtocol.value || null,
      errorMessage: "请选择调试目标",
      responseBody: ""
    };
    return;
  }
  if (!activeProtocol.value) {
    responseTab.value = "summary";
    testResult.value = {
      status: "ERROR",
      httpStatus: null,
      modelCode: null,
      protocol: null,
      errorMessage: "当前目标没有可调试的接口协议",
      responseBody: ""
    };
    return;
  }
  if (parsedTemplateBody.value.error) {
    responseTab.value = "summary";
    testResult.value = {
      status: "ERROR",
      httpStatus: null,
      modelCode: resolvedModel.value,
      protocol: activeProtocol.value,
      errorMessage: "请求模板不是合法 JSON",
      responseBody: currentTemplateText.value || ""
    };
    return;
  }

  loading.value = true;
  testResult.value = null;
  try {
    const requestBody = parsedTemplateBody.value.value || {};
    testResult.value = await testRoutingRule(props.ruleId, {
      secretKey: testForm.secretKey.trim(),
      model: requestBody.model || resolvedModel.value,
      prompt: inferPrompt(requestBody, activeProtocol.value),
      protocol: activeProtocol.value,
      targetType: activeTarget.value.targetType,
      targetId: activeTarget.value.targetId,
      requestBody
    }, { silentError: true });
    responseTab.value = "body";
  } catch (error) {
    responseTab.value = "body";
    testResult.value = normalizeErrorResult(error);
  } finally {
    loading.value = false;
  }
}

function inferPrompt(body, protocol) {
  const code = normalizeProtocolCode(protocol);
  if (code === "CHAT_COMPLETIONS" || code === "MESSAGES") {
    const first = Array.isArray(body?.messages) ? body.messages[0] : null;
    return typeof first?.content === "string" ? first.content : null;
  }
  if (code === "RESPONSES" || code === "EMBEDDINGS" || code === "AUDIO_SPEECH") {
    if (typeof body?.input === "string") return body.input;
    if (Array.isArray(body?.input) && typeof body.input[0] === "string") return body.input[0];
    return null;
  }
  if (code === "COMPLETIONS" || code === "IMAGE_GENERATIONS" || code === "VIDEO_GENERATIONS") {
    return typeof body?.prompt === "string" ? body.prompt : null;
  }
  if (code === "RERANK") {
    return typeof body?.query === "string" ? body.query : null;
  }
  return null;
}

function normalizeErrorResult(error) {
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
      errorMessage: data.message || data.error || error.message || "请求失败",
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

function serializeBody(body) {
  if (body == null || body === "") return "";
  if (typeof body === "string") return body;
  return stringifyJson(body);
}

function onClosed() {
  testResult.value = null;
  loading.value = false;
}
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

.request-template__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.request-template__head h4 {
  margin: 0 0 4px;
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
}

.request-template__head p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.request-template__hint {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.request-preview-collapse {
  margin-top: 10px;
}

.request-preview-collapse :deep(.el-collapse-item__header) {
  height: 34px;
  color: var(--el-text-color-regular);
  font-size: 12px;
}

.request-preview-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: 0;
}

.request-preview-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 0;
}

.debug-panel--response {
  min-height: 520px;
}

.debug-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.debug-tabs :deep(.el-tabs__item) {
  font-size: 12px;
}

.debug-tabs :deep(.el-tabs__content) {
  min-height: 360px;
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

.result-meta__item--error .result-meta__value {
  color: var(--el-color-danger);
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

  .request-template__head {
    align-items: flex-start;
    flex-direction: column;
  }

  .debug-panel--response {
    min-height: 420px;
  }
}
</style>
