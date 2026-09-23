<template>
  <section class="list-page-block">
    <div class="list-page-filters">
      <slot />
      <div class="list-filter-actions">
        <el-button type="primary" @click="emit('query')">查询</el-button>
        <el-button @click="emit('reset')">重置</el-button>
      </div>
    </div>
  </section>
</template>

<script setup>
const emit = defineEmits(["query", "reset"]);
</script>
