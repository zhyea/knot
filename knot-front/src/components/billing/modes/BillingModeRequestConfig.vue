<template>
  <el-row :gutter="16">
    <el-col :span="12">
      <el-form-item label="每次请求单价">
        <el-input-number v-model="form.unitPrice" :min="0" :step="UNIT_PRICE_STEP" controls-position="right" style="width: 100%" />
      </el-form-item>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import {UNIT_PRICE_STEP} from "@/utils/billingPrice";
defineProps({
  form: { type: Object, required: true }
});
</script>
