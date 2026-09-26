<template>
  <el-descriptions :column="1" border>
    <el-descriptions-item v-for="item in items" :key="item.label" :label="item.label">
      {{ item.value || "-" }}
    </el-descriptions-item>
  </el-descriptions>
</template>

<script setup lang="ts">
import type { PropType } from "vue";

interface InfoItem {
  label: string;
  value: unknown;
}

defineProps({
  items: {
    type: Array as PropType<InfoItem[]>,
    default: (): InfoItem[] => []
  }
});
</script>
