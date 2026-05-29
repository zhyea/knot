<template>
  <div class="billing-mode-custom-config">
    <div class="billing-mode-custom-config__label">自定义配置</div>
    <JsonCodeEditor v-model="form.customConfigJson" min-height="320px" max-height="620px" />
  </div>
</template>

<script setup>
import JsonCodeEditor from "../../common/JsonCodeEditor.vue";

defineProps({
  form: { type: Object, required: true }
});
</script>

<style scoped>
.billing-mode-custom-config {
  width: 100%;
  margin-top: 4px;
}

.billing-mode-custom-config__label {
  margin-bottom: 8px;
  color: var(--knot-text-regular, #606266);
  font-size: 14px;
  line-height: 22px;
}
</style>
