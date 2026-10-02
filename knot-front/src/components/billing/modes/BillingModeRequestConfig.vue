<template>
  <el-row :gutter="16">
    <el-col :span="12">
      <el-form-item label="每次请求单价">
        <el-input-number v-model="form.unitPrice" :min="0" :step="0.0001" style="width: 100%" />
      </el-form-item>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
defineProps({
  form: { type: Object, required: true }
});
</script>
