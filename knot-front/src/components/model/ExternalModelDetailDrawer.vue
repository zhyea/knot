<template>
  <el-drawer
    :model-value="modelValue"
    title="外部模型详情"
    size="60%"
    class="drawer-with-scrollbar"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-scrollbar max-height="calc(100vh - 140px)">
      <el-tabs v-if="detail" type="border-card">
        <el-tab-pane label="表格">
        <el-descriptions :column="1" border class="external-model-detail">
        <el-descriptions-item label="模型名称">{{ detail.modelName || "—" }}</el-descriptions-item>
        <el-descriptions-item label="模型 ID">{{ detail.modelId || "—" }}</el-descriptions-item>
        <el-descriptions-item label="Slug">{{ detail.canonicalSlug || "—" }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.providerName || "—" }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ modelTypeLabel(detail.modelType) }}</el-descriptions-item>
        <el-descriptions-item label="上下文">{{ detail.contextLength || "—" }}</el-descriptions-item>
        <el-descriptions-item label="输入模态">{{ formatArray(detail.inputModalitiesJson) }}</el-descriptions-item>
        <el-descriptions-item label="输出模态">{{ formatArray(detail.outputModalitiesJson) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ formatDateTime(detail.modelCreatedAt || detail.updatedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="模型链接">
          <el-link v-if="detail.modelUrl" :href="detail.modelUrl" target="_blank" type="primary">
            {{ detail.modelUrl }}
          </el-link>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="描述">{{ detail.description || "—" }}</el-descriptions-item>
        </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="JSON">
          <JsonCodeEditor :model-value="rawJsonText" readonly min-height="225px" max-height="560px"/>
        </el-tab-pane>
      </el-tabs>
    </el-scrollbar>
  </el-drawer>
</template>

<script setup lang="ts">
import type {Dict} from "@/types";
import {type PropType, computed} from "vue";
import JsonCodeEditor from "../common/JsonCodeEditor.vue";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {formatDateTime, formatJsonArray, formatJsonText} from "@/utils/format";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  detail: {type: Object as PropType<Dict | null>, default: null}
});

const emit = defineEmits(["update:modelValue"]);

const {labelOf} = useEnumOptions();

const rawJsonText = computed(() => formatJsonText(props.detail?.rawJson, "\t", "—"));

function modelTypeLabel(code: string): string {
  if (!code) return "—";
  return labelOf("ModelTypeEnum", code, code);
}

function formatArray(value: unknown): string {
  return formatJsonArray(value);
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

</style>
