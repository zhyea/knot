<template>
  <FilterField :label="label" grow>
    <el-input
      v-model="value"
      :placeholder="placeholder"
      clearable
      @keyup.enter="emit('query')"
    />
  </FilterField>
</template>

<script setup>
import FilterField from "./FilterField.vue";

const value = defineModel({ type: String, default: "" });

defineProps({
  label: { type: String, default: "关键字" },
  placeholder: { type: String, default: "" }
});

const emit = defineEmits(["query"]);
</script>
