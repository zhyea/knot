<template>
  <el-drawer v-model="visible" :title="title" size="70%" class="drawer-with-scrollbar">
    <el-scrollbar max-height="calc(100vh - 140px)">
      <ScheduledTaskRunPanel
        v-if="visible"
        ref="runPanelRef"
        :fixed-task-code="taskCode"
      />
    </el-scrollbar>
  </el-drawer>
</template>

<script setup lang="ts">
import {computed, ref} from "vue";
import ScheduledTaskRunPanel from "./ScheduledTaskRunPanel.vue";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 执行记录按任务编码查询：只接标识，不复用列表行 */
  taskCode: {type: String, default: ""}
});

const emit = defineEmits(["update:modelValue"]);

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value)
});

const runPanelRef = ref();

const title = computed(() => (props.taskCode ? `执行记录 - ${props.taskCode}` : "执行记录"));

defineExpose({ runPanelRef });
</script>
