<template>
  <el-drawer
    :model-value="modelValue"
    title="外部模型详情"
    size="60%"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-descriptions v-if="detail" :column="1" border class="external-model-detail">
      <el-descriptions-item label="外部模型">{{ detail.sourceModelName || "—" }}</el-descriptions-item>
      <el-descriptions-item label="模型 ID">{{ detail.sourceModelId || "—" }}</el-descriptions-item>
      <el-descriptions-item label="供应商">{{ detail.sourceProviderName || "—" }}</el-descriptions-item>
      <el-descriptions-item label="类型">{{ detail.sourceLlmType || "—" }}</el-descriptions-item>
      <el-descriptions-item label="类型编码">{{ detail.sourceLlmTypeCode || "—" }}</el-descriptions-item>
      <el-descriptions-item label="上下文">{{ detail.sourceContextLength || "—" }}</el-descriptions-item>
      <el-descriptions-item label="更新时间">{{ formatDateTime(detail.sourceLastUpdateTime || detail.updatedAt) }}</el-descriptions-item>
      <el-descriptions-item label="来源链接">
        <el-link v-if="detail.sourceUrl" :href="detail.sourceUrl" target="_blank" type="primary">{{ detail.sourceUrl }}</el-link>
        <span v-else>—</span>
      </el-descriptions-item>
      <el-descriptions-item label="描述">{{ detail.sourceDescription || "—" }}</el-descriptions-item>
    </el-descriptions>
    <div class="raw-title">原始数据</div>
    <JsonCodeEditor :model-value="rawJsonText" readonly min-height="225px" max-height="560px" />
  </el-drawer>
</template>

<script setup>
import { computed } from "vue";
import JsonCodeEditor from "../common/JsonCodeEditor.vue";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  detail: { type: Object, default: null }
});

const emit = defineEmits(["update:modelValue"]);

const rawJsonText = computed(() => formatJson(props.detail?.rawJson));

function formatDateTime(value) {
  if (!value) return "—";
  return String(value).replace("T", " ").slice(0, 19);
}

function formatJson(value) {
  if (!value) return "—";
  try {
    const parsed = typeof value === "string" ? JSON.parse(value) : value;
    return JSON.stringify(parsed, null, "\t");
  } catch {
    return String(value);
  }
}
</script>

<style scoped>
.external-model-detail :deep(.el-descriptions__label) {
  width: 128px;
  min-width: 128px;
  text-align: right;
  font-weight: 400;
  white-space: nowrap;
}

.external-model-detail :deep(.el-descriptions__content) {
  word-break: break-word;
}

.raw-title {
  margin: 18px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--knot-text, #303133);
}

</style>
