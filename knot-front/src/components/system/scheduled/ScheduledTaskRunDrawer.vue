<template>
  <el-drawer v-model="visible" :title="title" size="70%" class="drawer-with-scrollbar">
    <el-scrollbar max-height="calc(100vh - 140px)">
      <ScheduledTaskRunPanel
        v-if="visible"
        ref="runPanelRef"
        :fixed-task-code="task?.taskCode"
      />
    </el-scrollbar>
  </el-drawer>
</template>

<script setup>
import { computed, ref } from "vue";
import ScheduledTaskRunPanel from "./ScheduledTaskRunPanel.vue";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  task: { type: Object, default: null }
});

const emit = defineEmits(["update:modelValue"]);

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value)
});

const runPanelRef = ref();

const title = computed(() => {
  if (!props.task) {
    return "执行记录";
  }
  return `执行记录 - ${props.task.taskName || props.task.taskCode}`;
});

defineExpose({ runPanelRef });
</script>
