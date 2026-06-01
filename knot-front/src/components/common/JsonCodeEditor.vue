<template>
  <div class="json-editor" :class="{ 'json-editor--readonly': readonly }">
    <div v-if="!readonly" class="json-editor__toolbar">
      <el-button size="small" text @click="formatCurrentJson">格式化</el-button>
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

const displayText = computed(() => {
  if (props.readonly) {
    return formatJsonText(props.modelValue) || "—";
  }
  return props.modelValue || "";
});

const highlightedJson = computed(() => highlightJson(displayText.value));
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

function formatJsonText(value) {
  if (!value) {
    return "";
  }
  try {
    const parsed = typeof value === "string" ? JSON.parse(value) : value;
    return JSON.stringify(parsed, null, "\t");
  } catch {
    return String(value);
  }
}

function highlightJson(value) {
  const escaped = escapeHtml(value);
  return escaped.replace(
    /("(?:\\u[\da-fA-F]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(?:true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g,
    (match) => {
      let cls = "json-number";
      if (match.startsWith("\"")) {
        cls = match.endsWith(":") ? "json-key" : "json-string";
      } else if (match === "true" || match === "false") {
        cls = "json-boolean";
      } else if (match === "null") {
        cls = "json-null";
      }
      return `<span class="${cls}">${match}</span>`;
    }
  );
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}
</script>

<style scoped>
.json-editor {
  border: 1px solid var(--knot-border, #e4e7ed);
  background: var(--knot-fill-light, #f5f7fa);
}

.json-editor__toolbar {
  display: flex;
  justify-content: flex-end;
  padding: 4px 8px;
  border-bottom: 1px solid var(--knot-border, #e4e7ed);
  background: var(--knot-surface, #fff);
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

.json-editor__highlight {
  pointer-events: none;
  color: #303133;
}

.json-editor__body--editable .json-editor__highlight {
  min-width: max-content;
  transform-origin: left top;
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
</style>
