<template>
  <div class="cron-input">
    <el-input
      :model-value="modelValue"
      placeholder="请输入 Quartz Cron，如 0 0 3 * * ?"
      @update:model-value="emit('update:modelValue', ($event || '').trim())"
    />
    <div class="cron-input__hint">
      Quartz Cron 使用 6 段表达式：秒 分 时 日 月 周
    </div>
  </div>
</template>

<script setup>
defineProps({
  modelValue: { type: String, default: "" }
});

const emit = defineEmits(["update:modelValue"]);
</script>

<style scoped>
.cron-input {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.cron-input__hint {
  color: #909399;
  font-size: 12px;
}
</style>
