<template>
  <el-row :gutter="16">
    <el-col :span="12">
      <el-form-item label="非缓存输入">
        <el-input-number v-model="form.inputUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="12">
      <el-form-item label="输出">
        <el-input-number v-model="form.outputUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="12">
      <el-form-item label="缓存读取">
        <el-input-number v-model="form.cacheReadUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col v-if="form.cacheWriteMode !== 'ttl'" :span="12">
      <el-form-item label="缓存写">
        <el-input-number v-model="form.cacheWriteUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col v-if="form.cacheWriteMode === 'ttl'" :span="12">
      <el-form-item label="缓存写(5m)">
        <el-input-number v-model="form.cacheWrite5mUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col v-if="form.cacheWriteMode === 'ttl'" :span="12">
      <el-form-item label="缓存写(1h)">
        <el-input-number v-model="form.cacheWrite1hUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="12">
      <el-form-item label="缓存写方式">
        <el-radio-group v-model="form.cacheWriteMode">
          <el-radio-button value="standard">缓存写</el-radio-button>
          <el-radio-button value="ttl">缓存写(5m)/(1h)</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
defineProps({
  form: { type: Object, required: true }
});
</script>
