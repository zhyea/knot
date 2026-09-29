<template>
  <el-row :gutter="16">
    <el-col :span="12">
      <el-form-item label="非缓存输入单价">
        <el-input-number v-model="form.inputUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="12">
      <el-form-item label="输出单价">
        <el-input-number v-model="form.outputUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="12">
      <el-form-item label="缓存读取单价">
        <el-input-number v-model="form.cacheReadUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="12">
      <el-form-item label="缓存写入单价">
        <el-input-number v-model="form.cacheWriteUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="12">
      <el-form-item label="缓存写入(5m)单价">
        <el-input-number v-model="form.cacheWrite5mUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="12">
      <el-form-item label="缓存写入(1h)单价">
        <el-input-number v-model="form.cacheWrite1hUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
defineProps({
  form: { type: Object, required: true }
});
</script>
