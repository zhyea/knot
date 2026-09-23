<template>
  <div class="list-filter-item" :class="{ 'list-filter-item--grow': grow }">
    <span class="list-filter-label">{{ label }}</span>
    <div class="list-filter-field" :style="controlStyle">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  label: { type: String, default: "" },
  /** 自适应占满剩余宽度，关键字输入一般用这个 */
  grow: { type: Boolean, default: false },
  /** 固定控件宽度，如 220 或 "220px" */
  width: { type: [Number, String], default: null }
});

const controlStyle = computed(() => {
  if (props.width === null || props.width === "") {
    return null;
  }
  return { width: typeof props.width === "number" ? `${props.width}px` : props.width };
});
</script>

<style scoped>
.list-filter-field {
  display: flex;
  min-width: 0;
}

.list-filter-field > :deep(.el-select),
.list-filter-field > :deep(.el-input),
.list-filter-field > :deep(.el-date-editor) {
  width: 100%;
}

.list-filter-item--grow .list-filter-field {
  flex: 1 1 auto;
}
</style>
