<template>
  <el-drawer v-model="visible" title="日志详情" size="45%" class="drawer-with-scrollbar">
    <el-scrollbar max-height="calc(100vh - 140px)">
    <el-descriptions v-if="log" :column="2" border>
      <el-descriptions-item label="模块">{{ log.module }}</el-descriptions-item>
      <el-descriptions-item label="操作">{{ log.operation }}</el-descriptions-item>
      <el-descriptions-item label="实体类型">{{ log.entityType }}</el-descriptions-item>
      <el-descriptions-item label="实体ID">{{ log.entityId }}</el-descriptions-item>
      <el-descriptions-item label="实体名称">{{ log.entityName }}</el-descriptions-item>
      <el-descriptions-item label="操作人">{{ log.operatorName || "-" }}</el-descriptions-item>
      <el-descriptions-item label="IP地址">{{ log.ipAddress }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag :type="log.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
          {{ statusLabel(log.status) }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="执行时间(ms)">{{ log.executionTime }}</el-descriptions-item>
      <el-descriptions-item label="操作时间">{{ log.createdAt }}</el-descriptions-item>
      <el-descriptions-item label="描述" :span="2">{{ log.description }}</el-descriptions-item>
      <el-descriptions-item label="旧值" :span="2">
        <el-input v-if="log.oldValue" :model-value="log.oldValue" type="textarea" :rows="4" readonly />
        <span v-else>-</span>
      </el-descriptions-item>
      <el-descriptions-item label="新值" :span="2">
        <el-input v-if="log.newValue" :model-value="log.newValue" type="textarea" :rows="4" readonly />
        <span v-else>-</span>
      </el-descriptions-item>
      <el-descriptions-item v-if="log.errorMsg" label="错误信息" :span="2">
        <el-input :model-value="log.errorMsg" type="textarea" :rows="4" readonly />
      </el-descriptions-item>
    </el-descriptions>
    </el-scrollbar>
  </el-drawer>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  log: { type: Object, default: null },
  statusLabel: { type: Function, required: true }
});

const emit = defineEmits(["update:modelValue"]);

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value)
});
</script>
