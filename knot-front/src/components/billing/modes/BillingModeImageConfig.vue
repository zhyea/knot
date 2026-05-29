<template>
  <el-row :gutter="16">
    <el-col :span="8">
      <el-form-item label="每张单价">
        <el-input-number v-model="form.unitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="8">
      <el-form-item label="分辨率">
        <el-input v-model="form.imageResolution" placeholder="如 1024x1024" />
      </el-form-item>
    </el-col>
    <el-col :span="8">
      <el-form-item label="质量档位">
        <el-input v-model="form.imageQuality" placeholder="如 standard / hd" />
      </el-form-item>
    </el-col>
  </el-row>
</template>

<script setup>
defineProps({
  form: { type: Object, required: true }
});
</script>
