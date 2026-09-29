<template>
  <el-alert title="固定价直接使用上方基础价格，无进阶定价明细。" type="info" :closable="false" class="pricing-plan-fixed"/>
</template>

<script setup lang="ts">
defineProps({
  form: { type: Object, required: true }
});
</script>

<style scoped>
.pricing-plan-fixed {
  width: 100%;
  margin-top: 4px;
}
</style>
