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
import { escapeHtml, highlightJsonHtml } from "../../utils/format";

const props = defineProps({
  code: { type: String, default: "" },
  copyable: { type: Boolean, default: true },
  language: { type: String, default: "shell" }
});

const highlighted = computed(() => {
  const text = props.code || "";
  if (props.language === "json") {
    return text ? highlightJsonHtml(text) : '<span class="sh-muted">（暂无内容）</span>';
  }
  return highlightShell(text);
});

function highlightShell(text) {
  if (!text) {
    return '<span class="sh-muted">（暂无内容）</span>';
  }
  let html = escapeHtml(text);
  html = html.replace(/\b(curl)\b/g, '<span class="sh-cmd">$1</span>');
  html = html.replace(/(-[A-Za-z]+)/g, '<span class="sh-flag">$1</span>');
  html = html.replace(/(https?:\/\/[^\s'\\]+)/g, '<span class="sh-url">$1</span>');
  html = html.replace(/('(?:[^'\\]|\\.)*')/g, '<span class="sh-str">$1</span>');
  html = html.replace(/\\$/gm, '<span class="sh-cont">\\</span>');
  return html;
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
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 0;
  overflow: hidden;
}

.shell-code-scrollbar {
  max-height: 220px;
}

.shell-code-block {
  margin: 0;
  padding: 14px 16px;
  padding-right: 72px;
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-all;
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

.shell-code-block :deep(.sh-cont),
.shell-code-block :deep(.sh-muted) {
  color: #808080;
}

.shell-code-block :deep(.json-key) {
  color: #9cdcfe;
}

.shell-code-block :deep(.json-string) {
  color: #ce9178;
}

.shell-code-block :deep(.json-number) {
  color: #b5cea8;
}

.shell-code-block :deep(.json-boolean),
.shell-code-block :deep(.json-null) {
  color: #569cd6;
}

.copy-btn {
  position: absolute;
  right: 8px;
  top: 6px;
  background: rgb(30 30 30 / 88%);
}
</style>
