<template>
  <CodeBlockView
    class="shell-code-wrap"
    :copy-text="props.code"
    :copyable="copyable"
    theme="dark"
    :max-height="maxHeight"
  >
    <pre class="shell-code-block"><code v-html="highlighted"></code></pre>
  </CodeBlockView>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {escapeHtml, highlightJsonHtml} from "@/utils/format";
import CodeBlockView from "./CodeBlockView.vue";

const props = withDefaults(
  defineProps<{
    code?: string;
    copyable?: boolean;
    language?: string;
    /** 滚动区高度上限，默认 220px；外层可用 !important 覆写以撑满 */
    maxHeight?: string;
  }>(),
  {
    code: "",
    copyable: true,
    language: "shell",
    maxHeight: "220px"
  }
);

const highlighted = computed(() => {
  const text = props.code || "";
  if (props.language === "json") {
    return text ? highlightJsonHtml(text) : '<span class="sh-muted">（暂无内容）</span>';
  }
  return highlightShell(text);
});

function highlightShell(text: string): string {
  if (!text) {
    return '<span class="sh-muted">（暂无内容）</span>';
  }
  // 单次扫描：在已转义文本上按 token 包裹，避免「先插 <span class="sh-cmd"> 再被 (-[A-Za-z]+)
  // 二次匹配到 class 里的 -cmd」导致生成 <span class="sh<span class="sh-flag">-cmd</span>"> 这类破损结构。
  const escaped = escapeHtml(text);
  return escaped.replace(
    /curl|https?:\/\/[^\s'\\]+|'(?:[^'\\]|\\.)*'|\\$|-[A-Za-z]+/gm,
    (m) => {
      if (m === "curl") return '<span class="sh-cmd">curl</span>';
      if (m.startsWith("http")) return `<span class="sh-url">${m}</span>`;
      if (m.startsWith("'")) return `<span class="sh-str">${m}</span>`;
      if (m === "\\") return '<span class="sh-cont">\\</span>';
      return `<span class="sh-flag">${m}</span>`;
    }
  );
}
</script>

<style scoped>
.shell-code-wrap {
  position: relative;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 0;
  overflow: hidden;
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
</style>
