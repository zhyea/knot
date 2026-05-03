<template>
  <el-tag :type="active ? 'success' : 'info'" size="small">{{ active ? "是" : "否" }}</el-tag>
</template>

<script setup>
defineProps({
  active: {
    type: Boolean,
    required: true
  }
});
</script>
