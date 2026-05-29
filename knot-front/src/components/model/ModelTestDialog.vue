<template>
  <el-dialog
    :model-value="modelValue"
    title="模型联调测试"
    width="480px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-width="90px">
      <el-form-item label="Prompt">
        <el-input v-model="form.prompt" type="textarea" :rows="4" />
      </el-form-item>
      <el-form-item label="温度">
        <el-input-number v-model="form.temperature" :step="0.1" :min="0" :max="2" />
      </el-form-item>
      <el-form-item label="Max tokens">
        <el-input-number v-model="form.maxTokens" :min="1" />
      </el-form-item>
    </el-form>
    <el-alert v-if="result" type="success" :closable="false" class="mt">
      <template #title>输出（演示）</template>
      <div>{{ result.output }}</div>
      <div class="sub">延迟 {{ result.latencyMs }} ms · token {{ result.tokenUsage }}</div>
    </el-alert>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button type="primary" :loading="loading" @click="runTest">发送</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from "vue";
import { testModel } from "../../api/models";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  modelId: { type: [Number, String], default: null }
});

const emit = defineEmits(["update:modelValue"]);

const loading = ref(false);
const result = ref(null);
const form = reactive({ prompt: "hello", temperature: 0.7, maxTokens: 256 });

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      result.value = null;
    }
  }
);

async function runTest() {
  if (!props.modelId) return;
  loading.value = true;
  try {
    result.value = await testModel(props.modelId, {
      prompt: form.prompt,
      temperature: form.temperature,
      maxTokens: form.maxTokens
    });
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.mt {
  margin-top: 12px;
}

.sub {
  margin-top: 6px;
  font-size: 12px;
  color: #606266;
}
</style>
