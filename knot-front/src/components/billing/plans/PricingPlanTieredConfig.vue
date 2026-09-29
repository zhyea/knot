<template>
  <div class="pricing-plan-tiered">
    <div class="pricing-plan-tiered__label">阶梯配置（tier：每档 condition + 完整 unitPrices，单位区间含边界、不重叠）</div>
    <JsonCodeEditor v-model="form.tierJson" min-height="320px" max-height="620px" />
  </div>
</template>

<script setup lang="ts">
import JsonCodeEditor from "../../common/JsonCodeEditor.vue";

defineProps({
  form: { type: Object, required: true }
});
</script>

<style scoped>
.pricing-plan-tiered {
  width: 100%;
  margin-top: 4px;
}

.pricing-plan-tiered__label {
  margin-bottom: 8px;
  color: var(--knot-text-regular, #606266);
  font-size: 14px;
  line-height: 22px;
}
</style>
