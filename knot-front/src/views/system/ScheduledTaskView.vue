<template>
  <PageSection title="定时任务">
    <ScheduledTaskConfigPanel
        ref="configPanelRef"
        @create="openTaskDrawer()"
        @edit="openTaskDrawer"
        @logs="openRunDrawer"
        @triggered="onTaskTriggered"
    />

    <ScheduledTaskFormDrawer
        v-model="taskDrawer"
        :task="editingTask"
        @saved="onTaskSaved"
    />

    <ScheduledTaskRunDrawer v-model="runDrawer" :task="runTask" />
  </PageSection>
</template>

<script setup>
import {ref} from "vue";
import {ElMessage} from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import ScheduledTaskConfigPanel from "../../components/system/scheduled/ScheduledTaskConfigPanel.vue";
import ScheduledTaskFormDrawer from "../../components/system/scheduled/ScheduledTaskFormDrawer.vue";
import ScheduledTaskRunDrawer from "../../components/system/scheduled/ScheduledTaskRunDrawer.vue";

const taskDrawer = ref(false);
const runDrawer = ref(false);
const editingTask = ref(null);
const runTask = ref(null);
const configPanelRef = ref();

function openTaskDrawer(task) {
  editingTask.value = task || null;
  taskDrawer.value = true;
}

function onTaskSaved() {
  ElMessage.success("保存成功");
  configPanelRef.value?.load();
}

function openRunDrawer(task) {
  runTask.value = task;
  runDrawer.value = true;
}

function onTaskTriggered(task) {
  ElMessage.success("已提交执行");
  openRunDrawer(task);
}
</script>

<style scoped>
.task-panel {
  background: var(--knot-surface, #fff);
  border: 1px solid var(--knot-border, #dcdfe6);
  padding: 16px;
}
</style>
