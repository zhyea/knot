<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="640px"
    destroy-on-close
    class="json-code-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <pre class="json-code-block"><code v-html="highlightedJson"></code></pre>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: "JSON 预览" },
  data: { type: [Object, Array, String], default: null }
});

const emit = defineEmits(["update:modelValue"]);

const formattedJson = computed(() => {
  if (props.data == null) {
    return "";
  }
  if (typeof props.data === "string") {
    try {
      return JSON.stringify(JSON.parse(props.data), null, 2);
    } catch {
      return props.data;
    }
  }
  try {
    return JSON.stringify(props.data, null, 2);
  } catch {
    return String(props.data);
  }
});

const highlightedJson = computed(() => escapeHtml(formattedJson.value)
  .replace(/"([^"\\]*(?:\\.[^"\\]*)*)"(?=\s*:)/g, '<span class="json-key">"$1"</span>')
  .replace(/: "([^"\\]*(?:\\.[^"\\]*)*)"/g, ': <span class="json-string">"$1"</span>')
  .replace(/: (-?\d+(?:\.\d+)?)/g, ': <span class="json-number">$1</span>')
  .replace(/: (true|false|null)/g, ': <span class="json-literal">$1</span>'));

function escapeHtml(text) {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}
</script>

<style scoped>
.json-code-block {
  margin: 0;
  padding: 16px;
  background: #1e1e1e;
  border-radius: 6px;
  overflow: auto;
  max-height: 60vh;
  font-family: Consolas, "Courier New", monospace;
  font-size: 13px;
  line-height: 1.55;
  color: #d4d4d4;
  white-space: pre;
}

.json-code-block :deep(.json-key) {
  color: #9cdcfe;
}

.json-code-block :deep(.json-string) {
  color: #ce9178;
}

.json-code-block :deep(.json-number) {
  color: #b5cea8;
}

.json-code-block :deep(.json-literal) {
  color: #569cd6;
}
</style>
