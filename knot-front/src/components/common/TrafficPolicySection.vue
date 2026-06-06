<template>
  <div class="strategy-config">
    <div v-if="showHeader" class="strategy-config__head">
      <div v-if="title || description">
        <h3 v-if="title">{{ title }}</h3>
        <p v-if="description">{{ description }}</p>
      </div>
      <slot name="extra" />
    </div>

    <el-row v-if="isGrid" :gutter="gutter" class="strategy-config__grid">
      <el-col :span="24 / columns">
        <el-form-item :label="rateLabel">
          <KvEditor v-model="rateLimitModel" value-mode="number" />
        </el-form-item>
      </el-col>
      <el-col :span="24 / columns">
        <el-form-item :label="quotaLabel">
          <KvEditor v-model="quotaModel" value-mode="number" />
        </el-form-item>
      </el-col>
    </el-row>

    <template v-else>
      <el-form-item :label="rateLabel">
        <KvEditor v-model="rateLimitModel" value-mode="number" />
      </el-form-item>
      <el-form-item :label="quotaLabel">
        <KvEditor v-model="quotaModel" value-mode="number" />
      </el-form-item>
    </template>
  </div>
</template>

<script setup>
import { computed, useSlots } from "vue";
import KvEditor from "./KvEditor.vue";

const props = defineProps({
  title: { type: String, default: "" },
  description: { type: String, default: "" },
  rateLabel: { type: String, default: "频控策略" },
  quotaLabel: { type: String, default: "额度策略" },
  rateLimit: { type: Object, default: () => ({}) },
  quota: { type: Object, default: () => ({}) },
  columns: { type: Number, default: 1 },
  gutter: { type: Number, default: 16 }
});

const emit = defineEmits(["update:rateLimit", "update:quota"]);
const slots = useSlots();

const rateLimitModel = computed({
  get: () => props.rateLimit,
  set: (value) => emit("update:rateLimit", value)
});

const quotaModel = computed({
  get: () => props.quota,
  set: (value) => emit("update:quota", value)
});

const showHeader = computed(() => !!props.title || !!props.description || !!slots.extra);
const isGrid = computed(() => props.columns > 1);
</script>

<style scoped>
.strategy-config {
  display: block;
}

.strategy-config__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.strategy-config__head h3 {
  margin: 0 0 4px;
  color: var(--knot-text, #303133);
  font-size: 15px;
  font-weight: 600;
}

.strategy-config__head p {
  margin: 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.strategy-config__grid :deep(.el-form-item) {
  margin-bottom: 0;
}

@media (max-width: 900px) {
  .strategy-config__head {
    display: block;
  }
}
</style>
