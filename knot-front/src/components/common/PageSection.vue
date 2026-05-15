<template>
  <div class="page-section">
    <h2 class="title">{{ title }}</h2>
    <div v-if="$slots.default" class="slot-body">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    required: true
  }
});
</script>

<style scoped>
.page-section {
  background: #fff;
  border-radius: 4px;
  padding: 20px;
  width: 100%;
}

.title {
  margin: 0 0 16px 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.slot-body {
  margin-top: 16px;
}
</style>
