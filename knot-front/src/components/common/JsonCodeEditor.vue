<template>
  <div class="json-editor" :class="{ 'json-editor--readonly': readonly }">
    <div class="json-editor__actions">
      <el-button
        v-if="!readonly"
        class="json-editor__action-btn"
        size="small"
        text
        type="primary"
        @click="formatCurrentJson"
      >
        格式化
      </el-button>
      <el-button
        class="json-editor__action-btn"
        size="small"
        text
        type="primary"
        @click="copyCurrentJson"
      >
        复制
      </el-button>
    </div>
    <el-scrollbar ref="scrollbarRef" class="json-editor__scrollbar" :style="bodyStyle" @scroll="syncWrapScroll">
      <div
        class="json-editor__body"
        :class="{ 'json-editor__body--editable': !readonly, 'json-editor__body--readonly': readonly }"
      >
        <pre class="json-editor__highlight" :style="highlightStyle" aria-hidden="true"><code v-html="highlightedJson"></code></pre>
        <textarea
          v-if="!readonly"
          ref="inputRef"
          class="json-editor__input"
          :value="modelValue"
          wrap="off"
          spellcheck="false"
          @input="emit('update:modelValue', $event.target.value)"
          @keydown="handleKeydown"
          @scroll="syncInputScroll"
        />
      </div>
    </el-scrollbar>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";
import { ElMessage } from "element-plus";
import { escapeHtml, formatJsonText } from "../../utils/format";

const props = defineProps({
  modelValue: { type: String, default: "" },
  readonly: { type: Boolean, default: false },
  minHeight: { type: String, default: "180px" },
  maxHeight: { type: String, default: "420px" }
});

const emit = defineEmits(["update:modelValue"]);

const scrollTop = ref(0);
const scrollLeft = ref(0);
const scrollbarRef = ref(null);
const inputRef = ref(null);
let syncSource = null;
const JSON_TOKEN_REGEX = /("(?:\\u[\da-fA-F]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(?:true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g;

const displayText = computed(() => {
  if (props.readonly) {
    return formatJsonText(props.modelValue) || "—";
  }
  return props.modelValue || "";
});

const highlightedJson = computed(() => renderHighlightedJson(displayText.value));
const bodyStyle = computed(() => ({
  minHeight: props.minHeight,
  maxHeight: props.maxHeight
}));
const highlightStyle = computed(() => {
  if (props.readonly) {
    return {};
  }
  return {
    transform: `translate(${-scrollLeft.value}px, ${-scrollTop.value}px)`
  };
});

function formatCurrentJson() {
  const formatted = formatJsonText(props.modelValue);
  if (!formatted) {
    ElMessage.warning("不是合法 JSON");
    return;
  }
  emit("update:modelValue", formatted);
}

async function copyCurrentJson() {
  const text = props.modelValue || "";
  if (!text) {
    ElMessage.warning("暂无可复制内容");
    return;
  }
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success("已复制");
  } catch {
    ElMessage.error("复制失败");
  }
}

function handleKeydown(event) {
  if (event.key !== "Tab") {
    return;
  }
  event.preventDefault();
  const target = event.target;
  const start = target.selectionStart;
  const end = target.selectionEnd;
  const value = target.value;
  const next = value.slice(0, start) + "\t" + value.slice(end);
  emit("update:modelValue", next);
  requestAnimationFrame(() => {
    target.selectionStart = start + 1;
    target.selectionEnd = start + 1;
  });
}

function syncInputScroll(event) {
  if (syncSource === "wrap") {
    return;
  }
  syncSource = "input";
  scrollTop.value = event.target.scrollTop;
  scrollLeft.value = event.target.scrollLeft;
  scrollbarRef.value?.setScrollTop?.(scrollTop.value);
  scrollbarRef.value?.setScrollLeft?.(scrollLeft.value);
  requestAnimationFrame(() => {
    if (syncSource === "input") {
      syncSource = null;
    }
  });
}

function syncWrapScroll({ scrollTop: nextTop, scrollLeft: nextLeft }) {
  if (props.readonly || syncSource === "input") {
    return;
  }
  syncSource = "wrap";
  scrollTop.value = nextTop;
  scrollLeft.value = nextLeft;
  if (inputRef.value) {
    inputRef.value.scrollTop = nextTop;
    inputRef.value.scrollLeft = nextLeft;
  }
  requestAnimationFrame(() => {
    if (syncSource === "wrap") {
      syncSource = null;
    }
  });
}

function renderHighlightedJson(text) {
  const source = String(text ?? "");
  let html = "";
  let lastIndex = 0;
  source.replace(JSON_TOKEN_REGEX, (match, _token, _suffix, offset) => {
    html += visualizeWhitespaceText(source.slice(lastIndex, offset));
    html += `<span class="${resolveJsonTokenClass(match)}">${visualizeWhitespaceText(match)}</span>`;
    lastIndex = offset + match.length;
    return match;
  });
  html += visualizeWhitespaceText(source.slice(lastIndex));
  return html;
}

function resolveJsonTokenClass(match) {
  if (match.startsWith("\"")) {
    return match.endsWith(":") ? "json-key" : "json-string";
  }
  if (match === "true" || match === "false") {
    return "json-boolean";
  }
  if (match === "null") {
    return "json-null";
  }
  return "json-number";
}

function visualizeWhitespaceText(text) {
  return Array.from(String(text ?? ""), (char) => {
    if (char === "\t") {
      return '<span class="json-tab-visual">    </span>';
    }
    if (char === " ") {
      return '<span class="json-space-visual"> </span>';
    }
    return escapeHtml(char);
  }).join("");
}

</script>

<style scoped>
.json-editor {
  position: relative;
  border: 1px solid var(--knot-border, #e4e7ed);
  background: var(--knot-fill-light, #f5f7fa);
}

.json-editor__actions {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 4px;
}

.json-editor__action-btn {
  min-height: 24px;
  padding: 4px 8px;
  background: rgb(255 255 255 / 88%);
  backdrop-filter: blur(4px);
}

.json-editor__body {
  position: relative;
  min-height: 100%;
}

.json-editor__body--editable {
  overflow: hidden;
}

.json-editor__scrollbar {
  width: 100%;
}

.json-editor__highlight,
.json-editor__highlight code,
.json-editor__input {
  box-sizing: border-box;
  width: 100%;
  min-height: inherit;
  margin: 0;
  padding: 12px;
  border: none;
  outline: none;
  font-family: Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
  tab-size: 4;
  white-space: pre;
}

.json-editor__highlight code {
  display: block;
}

.json-editor__highlight {
  pointer-events: none;
  color: #303133;
}

.json-editor__body--editable .json-editor__highlight {
  min-width: max-content;
  transform-origin: left top;
  padding-right: 120px;
}

.json-editor__input {
  position: absolute;
  inset: 0;
  height: 100%;
  resize: none;
  overflow: auto;
  background: transparent;
  color: transparent;
  caret-color: #303133;
  scrollbar-width: none;
  padding-right: 120px;
}

.json-editor__input::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.json-editor--readonly .json-editor__highlight {
  min-height: 0;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.json-editor :deep(.json-key) {
  color: #7c3aed;
}

.json-editor :deep(.json-string) {
  color: #047857;
}

.json-editor :deep(.json-number) {
  color: #b45309;
}

.json-editor :deep(.json-boolean) {
  color: #1d4ed8;
}

.json-editor :deep(.json-null) {
  color: #6b7280;
}

.json-editor :deep(.json-space-visual),
.json-editor :deep(.json-tab-visual) {
  position: relative;
  display: inline-block;
  white-space: pre;
  line-height: inherit;
  color: transparent;
  user-select: none;
  pointer-events: none;
}

.json-editor :deep(.json-space-visual) {
  vertical-align: baseline;
}

.json-editor :deep(.json-space-visual::after) {
  content: "";
  position: absolute;
  left: 50%;
  top: 58%;
  width: 1px;
  height: 1px;
  border-radius: 50%;
  background: #dc2626;
  opacity: 0.75;
  transform: translate(-50%, -50%);
}

.json-editor :deep(.json-tab-visual) {
  vertical-align: baseline;
  background-image: linear-gradient(#dc2626, #dc2626);
  background-repeat: no-repeat;
  background-size: calc(100% - 0.8ch) 0.5px;
  background-position: left 0.2ch center;
}

.json-editor :deep(.json-tab-visual::after) {
  content: "";
  position: absolute;
  right: 0.06ch;
  top: 50%;
  width: 0.6ch;
  height: 0.6ch;
  border-top: 1px solid #dc2626;
  border-right: 1px solid #dc2626;
  transform: translateY(-50%) rotate(45deg);
  transform-origin: center;
}
</style>
