<template>
  <el-row :gutter="16">
    <el-col :span="8">
      <el-form-item label="每秒单价">
        <el-input-number v-model="form.unitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="8">
      <el-form-item label="720P 每秒">
        <el-input-number v-model="form.videoPrice720p" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="8">
      <el-form-item label="1080P 每秒">
        <el-input-number v-model="form.videoPrice1080p" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
  </el-row>
</template>

<script setup>
defineProps({
  form: { type: Object, required: true }
});
</script>
