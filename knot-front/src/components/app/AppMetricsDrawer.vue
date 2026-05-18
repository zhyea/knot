<template>
  <el-drawer
    :model-value="modelValue"
    :title="drawerTitle"
    size="40%"
    destroy-on-close
    v-loading="loading"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >
    <el-descriptions v-if="metrics" :column="1" border>
      <el-descriptions-item label="总请求">{{ metrics.totalRequests }}</el-descriptions-item>
      <el-descriptions-item label="成功">{{ metrics.successRequests }}</el-descriptions-item>
      <el-descriptions-item label="失败">{{ metrics.failedRequests }}</el-descriptions-item>
      <el-descriptions-item label="Token 用量">{{ metrics.tokenUsage }}</el-descriptions-item>
    </el-descriptions>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { getAppMetrics } from "../../api/apps";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  appId: { type: Number, default: null },
  appName: { type: String, default: "" }
});

const emit = defineEmits(["update:modelValue"]);

const loading = ref(false);
const metrics = ref(null);

const drawerTitle = computed(() => {
  const name = props.appName?.trim();
  return name ? `调用指标（演示）— ${name}` : "调用指标（演示）";
});

async function loadMetrics() {
  if (!props.appId) return;
  loading.value = true;
  try {
    metrics.value = await getAppMetrics(props.appId);
  } finally {
    loading.value = false;
  }
}

watch(
  () => [props.modelValue, props.appId],
  ([visible, appId]) => {
    if (visible && appId) {
      loadMetrics();
    }
  }
);

function onClosed() {
  metrics.value = null;
}
</script>
