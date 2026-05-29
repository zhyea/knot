<template>
  <div>
    <div class="toolbar">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item v-if="!fixedTaskCode" label="任务编码">
          <el-input v-model="query.taskCode" placeholder="请输入任务编码" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="请选择状态">
            <el-option label="全部" value="" />
            <el-option label="运行中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILURE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="resetPage">查询</el-button>
        </el-form-item>
      </el-form>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe border size="small">
      <el-table-column prop="taskCode" label="任务编码" min-width="170" show-overflow-tooltip />
      <el-table-column prop="taskName" label="任务名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="nodeId" label="执行节点" min-width="170" show-overflow-tooltip />
      <el-table-column prop="executionMode" label="执行模式" width="100" align="center">
        <template #default="{ row }">{{ taskModeLabel(row.executionMode) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="runStatusType(row.status)" size="small">{{ runStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="affectedRows" label="影响行数" width="100" align="right" />
      <el-table-column prop="durationMs" label="耗时(ms)" width="100" align="right" />
      <el-table-column prop="startTime" label="开始时间" width="180" />
      <el-table-column prop="endTime" label="结束时间" width="180" />
      <el-table-column prop="errorMsg" label="错误信息" min-width="180" show-overflow-tooltip />
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        :page-sizes="[10, 20, 50]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { reactive } from "vue";
import { usePageList } from "../../../composables/usePageList";
import { listScheduledTaskRuns } from "../../../api/scheduledTasks";
import { runStatusLabel, runStatusType, taskModeLabel } from "../../../utils/scheduledTask";

const props = defineProps({
  fixedTaskCode: { type: String, default: "" }
});

const query = reactive({ taskCode: "", status: "" });

const {
  rows,
  loading,
  total,
  pageNum,
  pageSize,
  load,
  resetPage,
  onPageChange,
  onSizeChange
} = usePageList(listScheduledTaskRuns, { extra: query });

function focusTask(taskCode) {
  query.taskCode = taskCode || props.fixedTaskCode || "";
  query.status = "";
  resetPage();
}

if (props.fixedTaskCode) {
  query.taskCode = props.fixedTaskCode;
}

load();

defineExpose({ load, focusTask });
</script>

<style scoped>
.query-form {
  flex: 1;
}
</style>
