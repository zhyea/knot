<template>
  <PageSection>
    <div class="list-page-shell">
      <section class="list-page-block">
        <div class="list-page-filters">
          <div class="list-filter-item">
            <span class="list-filter-label">任务编码</span>
            <el-input
              v-model="query.taskCode"
              class="list-filter-control--wide"
              placeholder="请输入任务编码"
              clearable
              @keyup.enter="handleQuery"
            />
          </div>
          <div class="list-filter-item">
            <span class="list-filter-label">状态</span>
            <el-select v-model="query.status" class="list-filter-control" placeholder="请选择状态" clearable>
              <el-option label="启用" value="ENABLED" />
              <el-option label="禁用" value="DISABLED" />
            </el-select>
          </div>
          <div class="list-filter-actions">
            <el-button type="primary" @click="handleQuery">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </div>
        </div>
      </section>

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
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import ScheduledTaskConfigPanel from "../../components/system/scheduled/ScheduledTaskConfigPanel.vue";
import ScheduledTaskFormDrawer from "../../components/system/scheduled/ScheduledTaskFormDrawer.vue";
import ScheduledTaskRunDrawer from "../../components/system/scheduled/ScheduledTaskRunDrawer.vue";
import { listScheduledTasks } from "../../api/scheduledTasks";
import { usePageList } from "../../composables/usePageList";

const query = reactive({
  taskCode: "",
  status: ""
});

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listScheduledTasks, { extra: query });

const taskDrawer = ref(false);
const runDrawer = ref(false);
const editingTask = ref(null);
const runTask = ref(null);

function openTaskDrawer(task) {
  editingTask.value = task || null;
  taskDrawer.value = true;
}

function handleQuery() {
  return resetPage();
}

function handleReset() {
  query.taskCode = "";
  query.status = "";
  return resetPage();
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
