<template>
  <div class="shell-code-wrap">
    <el-scrollbar class="shell-code-scrollbar" max-height="220px">
      <pre class="shell-code-block"><code v-html="highlighted"></code></pre>
    </el-scrollbar>
    <el-button
      v-if="copyable"
      class="copy-btn"
      size="small"
      text
      type="primary"
      @click="copyCode"
    >
      复制
    </el-button>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { ElMessage } from "element-plus";

const props = defineProps({
  code: { type: String, default: "" },
  copyable: { type: Boolean, default: true },
  /** shell | json */
  language: { type: String, default: "shell" }
});

const highlighted = computed(() => {
  const text = props.code || "";
  if (props.language === "json") {
    return highlightJson(text);
  }
  return highlightShell(text);
});

function escapeHtml(text) {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function highlightJson(text) {
  if (!text) {
    return '<span class="sh-muted">（暂无内容）</span>';
  }
  return escapeHtml(text)
    .replace(/"([^"\\]*(?:\\.[^"\\]*)*)"(?=\s*:)/g, '<span class="json-key">"$1"</span>')
    .replace(/: "([^"\\]*(?:\\.[^"\\]*)*)"/g, ': <span class="json-str">"$1"</span>')
    .replace(/: (-?\d+(?:\.\d+)?)/g, ': <span class="json-num">$1</span>')
    .replace(/: (true|false|null)/g, ': <span class="json-lit">$1</span>');
}

function highlightShell(text) {
  if (!text) {
    return '<span class="sh-muted">（暂无内容）</span>';
  }
  let s = escapeHtml(text);
  s = s.replace(/\b(curl)\b/g, '<span class="sh-cmd">$1</span>');
  s = s.replace(/(-[A-Za-z]+)/g, '<span class="sh-flag">$1</span>');
  s = s.replace(/(https?:\/\/[^\s'\\]+)/g, '<span class="sh-url">$1</span>');
  s = s.replace(/('(?:[^'\\]|\\.)*')/g, '<span class="sh-str">$1</span>');
  s = s.replace(/\\$/gm, '<span class="sh-cont">\\</span>');
  return s;
}

async function copyCode() {
  if (!props.code) return;
  try {
    await navigator.clipboard.writeText(props.code);
    ElMessage.success("已复制");
  } catch {
    ElMessage.error("复制失败");
  }
}
</script>

<style scoped>
.shell-code-wrap {
  position: relative;
  border-radius: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
}

.shell-code-block {
  margin: 0;
  padding: 14px 16px 36px;
  background: #1e1e1e;
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.55;
  color: #d4d4d4;
  white-space: pre-wrap;
  word-break: break-all;
}

.shell-code-scrollbar {
  max-height: 220px;
}

.shell-code-block :deep(.sh-cmd) {
  color: #dcdcaa;
  font-weight: 600;
}

.shell-code-block :deep(.sh-flag) {
  color: #9cdcfe;
}

.shell-code-block :deep(.sh-str) {
  color: #ce9178;
}

.shell-code-block :deep(.sh-url) {
  color: #4ec9b0;
}

.shell-code-block :deep(.sh-cont) {
  color: #808080;
}

.shell-code-block :deep(.sh-muted) {
  color: #808080;
}

.shell-code-block :deep(.json-key) {
  color: #9cdcfe;
}

.shell-code-block :deep(.json-str) {
  color: #ce9178;
}

.shell-code-block :deep(.json-num) {
  color: #b5cea8;
}

.shell-code-block :deep(.json-lit) {
  color: #569cd6;
}

.copy-btn {
  position: absolute;
  right: 8px;
  bottom: 6px;
}
</style>
