<template>
  <el-row :gutter="16">
    <el-col :span="12">
      <el-form-item label="非缓存输入" class="billing-label-purple">
        <el-input-number v-model="form.inputUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="12">
      <el-form-item label="输出" class="billing-label-purple">
        <el-input-number v-model="form.outputUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="12">
      <el-form-item label="缓存读取" class="billing-label-purple">
        <el-input-number v-model="form.cacheReadUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col v-if="form.cacheWriteMode !== 'ttl'" :span="12">
      <el-form-item label="缓存写" class="billing-label-purple">
        <el-input-number v-model="form.cacheWriteUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col v-if="form.cacheWriteMode === 'ttl'" :span="12">
      <el-form-item label="缓存写(5m)" class="billing-label-purple">
        <el-input-number v-model="form.cacheWrite5mUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col v-if="form.cacheWriteMode === 'ttl'" :span="12">
      <el-form-item label="缓存写(1h)" class="billing-label-purple">
        <el-input-number v-model="form.cacheWrite1hUnitPrice" :min="0" :step="0.0001" :precision="6" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="12">
      <el-form-item label="缓存写方式" class="billing-label-purple">
        <el-select v-model="form.cacheWriteMode" style="width: 100%">
          <el-option label="缓存写" value="standard" />
          <el-option label="缓存写(5m)/(1h)" value="ttl" />
        </el-select>
      </el-form-item>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
defineProps({
  form: { type: Object, required: true }
});
</script>

<style scoped>
.billing-label-purple :deep(.el-form-item__label) {
  background: #f2e8f5;
  border-radius: 4px;
  padding: 0 8px;
}
</style>
