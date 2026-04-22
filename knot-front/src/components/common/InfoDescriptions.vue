<template>
  <el-descriptions :column="1" border>
    <el-descriptions-item v-for="item in items" :key="item.label" :label="item.label">
      {{ item.value || "-" }}
    </el-descriptions-item>
  </el-descriptions>
</template>

<script setup>
defineProps({
  items: {
    type: Array,
    default: () => []
  }
});
</script>
