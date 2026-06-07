<template>
  <div class="page-section">
    <h2 v-if="title" class="title">{{ title }}</h2>
    <div v-if="$slots.default" class="slot-body">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: ""
  }
});
</script>

<style scoped>
.page-section {
  background: var(--knot-surface, #fff);
  border-radius: 0;
  width: 100%;
}

.title {
  margin: 0 0 16px 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.slot-body {
  margin-top: 0;
  border: none;
}
</style>
