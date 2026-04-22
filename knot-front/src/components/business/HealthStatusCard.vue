<template>
  <el-card class="box-card">
    <template #header>
      <div class="card-header">
        <span>{{ title }}</span>
        <el-button type="primary" @click="$emit('refresh')">刷新状态</el-button>
      </div>
    </template>
    <InfoDescriptions :items="displayItems" />
  </el-card>
</template>

<script setup>
import { computed } from "vue";
import InfoDescriptions from "../common/InfoDescriptions.vue";

const props = defineProps({
  title: {
    type: String,
    default: "Knot AI Gateway"
  },
  health: {
    type: Object,
    required: true
  }
});

defineEmits(["refresh"]);

const displayItems = computed(() => [
  { label: "服务状态", value: props.health.status },
  { label: "应用时间", value: props.health.serverTime },
  { label: "数据库时间", value: props.health.dbTime }
]);
</script>

<style scoped>
.box-card {
  width: 100%;
  max-width: 720px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
