<template>
  <el-row :gutter="16">
    <el-col :span="12">
      <el-form-item label="输入 Token 单价">
        <el-input-number v-model="form.inputUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
  </el-row>
</template>

<script setup>
defineProps({
  form: { type: Object, required: true }
});
</script>
