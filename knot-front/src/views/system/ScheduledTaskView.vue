<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按任务编码、名称、处理器筛选"
          @query="handleQuery"
        />
        <FilterField label="状态" :width="180">
          <el-select v-model="query.status" placeholder="请选择状态" clearable>
            <el-option label="启用" value="ENABLED" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </FilterField>
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openTaskDrawer()">新增</el-button>
          </div>
        </div>

        <ScheduledTaskConfigPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :show-refresh="false"
          @edit="openTaskDrawer"
          @logs="openRunDrawer"
          @triggered="onTaskTriggered"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <ScheduledTaskFormDrawer
      v-model="taskDrawer"
      :task="editingTask"
      @saved="onTaskSaved"
    />

    <ScheduledTaskRunDrawer v-model="runDrawer" :task="runTask" />
  </PageSection>
</template>

<script setup>
import { ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import ScheduledTaskConfigPanel from "../../components/system/scheduled/ScheduledTaskConfigPanel.vue";
import ScheduledTaskFormDrawer from "../../components/system/scheduled/ScheduledTaskFormDrawer.vue";
import ScheduledTaskRunDrawer from "../../components/system/scheduled/ScheduledTaskRunDrawer.vue";
import { listScheduledTasks } from "../../api/scheduledTasks";
import { useListQuery } from "../../composables/useListQuery";

const {
  query,
  rows,
  loading,
  total,
  pageNum,
  pageSize,
  load,
  onPageChange,
  onSizeChange,
  resetPage,
  handleQuery,
  handleReset
} = useListQuery({ apiFn: listScheduledTasks, fields: { keyword: "", status: "" } });

const taskDrawer = ref(false);
const runDrawer = ref(false);
const editingTask = ref(null);
const runTask = ref(null);

function openTaskDrawer(task) {
  editingTask.value = task || null;
  taskDrawer.value = true;
}

function onTaskSaved() {
  ElMessage.success("保存成功");
  resetPage();
}

function openRunDrawer(task) {
  runTask.value = task;
  runDrawer.value = true;
}

function onTaskTriggered(task) {
  ElMessage.success("已提交执行");
  load();
  openRunDrawer(task);
}

load();
</script>
