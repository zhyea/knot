<template>
  <div class="page-section">
    <h2 class="title">{{ title }}</h2>
    <p v-if="description" class="desc">{{ description }}</p>
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
  },
  description: {
    type: String,
    default: ""
  }
});
</script>
