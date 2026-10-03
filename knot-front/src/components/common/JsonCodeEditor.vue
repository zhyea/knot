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
    <el-scrollbar class="json-editor__scrollbar" :style="bodyStyle">
      <div
        class="json-editor__body"
        :class="{ 'json-editor__body--editable': !readonly, 'json-editor__body--readonly': readonly }"
      >
        <pre class="json-editor__highlight" aria-hidden="true"><code
          v-html="highlightedJson"></code></pre>
        <textarea
          v-if="!readonly"
          class="json-editor__input"
          :value="modelValue"
          wrap="off"
          spellcheck="false"
          @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
          @keydown="handleKeydown"
        />
      </div>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {ElMessage} from "element-plus";
import {escapeHtml, formatJsonText} from "@/utils/format";

const props = defineProps({
  modelValue: {type: String, default: ""},
  readonly: {type: Boolean, default: false},
  minHeight: {type: String, default: "180px"},
  maxHeight: {type: String, default: "420px"},
  /** 固定滚动容器高度；只设置 max-height 时，el-scrollbar 无法稳定计算滚动区域 */
  height: {type: String, default: ""}
});

const emit = defineEmits(["update:modelValue"]);

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
  maxHeight: props.maxHeight,
  ...(props.height ? {height: props.height} : {})
}));

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

function handleKeydown(event: KeyboardEvent): void {
  if (event.key !== "Tab") {
    return;
  }
  event.preventDefault();
  const target = event.target as HTMLTextAreaElement;
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

function renderHighlightedJson(text: unknown): string {
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

function resolveJsonTokenClass(match: string): string {
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

function visualizeWhitespaceText(text: unknown): string {
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
  padding: 0;
}

.json-editor__highlight {
  pointer-events: none;
  color: #303133;
}

.json-editor__body--editable .json-editor__highlight {
  min-width: max-content;
  padding-right: 120px;
}

.json-editor__input {
  position: absolute;
  inset: 0;
  height: 100%;
  resize: none;
  overflow: hidden;
  background: transparent;
  color: transparent;
  caret-color: #303133;
  padding-right: 120px;
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
  background-image: linear-gradient(#fca5a5, #fca5a5);
  background-repeat: no-repeat;
  /* 线段末端顶到箭头左缘（0 间隙）；箭头右缘到 tab 盒右边界留0.38ch，
     叠加下一个 tab 线段的 0.2ch 起点间距 → 箭头与右侧横线视觉间距约 0.58ch */
  background-size: calc(100% - 1.42ch) 0.5px;
  background-position: left 0.2ch center;
}

.json-editor :deep(.json-tab-visual::after) {
  content: "";
  position: absolute;
  right: 0.5ch;
  top: 50%;
  width: 0.6ch;
  height: 0.6ch;
  border-top: 1px solid #fca5a5;
  border-right: 1px solid #fca5a5;
  transform: translateY(-50%) rotate(45deg);
  transform-origin: center;
}
</style>
