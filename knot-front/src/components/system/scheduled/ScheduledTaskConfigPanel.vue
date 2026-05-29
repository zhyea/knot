<template>
  <div>
    <div class="toolbar">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="任务编码">
          <el-input v-model="query.taskCode" placeholder="请输入任务编码" clearable/>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="请选择状态" clearable>
            <el-option label="启用" value="ENABLED"/>
            <el-option label="禁用" value="DISABLED"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="resetPage">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="toolbar-actions">
        <el-button type="primary" @click="emit('create')">新增</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="rows" stripe border size="small">
      <el-table-column prop="taskCode" label="任务编码" min-width="160" show-overflow-tooltip/>
      <el-table-column prop="taskName" label="任务名称" min-width="170" show-overflow-tooltip/>
      <el-table-column prop="handlerCode" label="处理器" min-width="170" show-overflow-tooltip/>
      <el-table-column prop="cronExpression" label="Cron表达式" width="120"/>
      <el-table-column prop="executionMode" label="执行模式" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.executionMode === 'SINGLE' ? 'warning' : 'success'" size="small">
            {{ taskModeLabel(row.executionMode) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
            {{ taskStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastFireAt" label="上次执行" width="160" align="center"/>
      <el-table-column prop="nextFireAt" label="下次执行" width="160" align="center"/>
      <el-table-column label="操作" width="120" fixed="right" align="center">
        <template #default="{ row }">
          <RowActions :actions="taskActions(row)" @action="(action) => handleAction(action, row)"/>
        </template>
      </el-table-column>
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
import {reactive} from "vue";
import {Edit, Tickets, VideoPlay} from "@element-plus/icons-vue";
import RowActions from "../../common/RowActions.vue";
import {usePageList} from "../../../composables/usePageList";
import {listScheduledTasks, triggerScheduledTask} from "../../../api/scheduledTasks";
import {taskModeLabel, taskStatusLabel} from "../../../utils/scheduledTask";

const emit = defineEmits(["create", "edit", "logs", "triggered"]);

const query = reactive({taskCode: "", status: "", handlerCode: ""});

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
} = usePageList(listScheduledTasks, {extra: query});

function taskActions(row) {
  return [
    {key: "edit", label: "编辑", icon: Edit},
    {key: "logs", label: "执行记录", icon: Tickets},
    {key: "run", label: "立即执行", icon: VideoPlay, disabled: row.status !== "ENABLED", confirm: "确认立即执行该任务？"}
  ];
}

async function handleAction(action, row) {
  if (action === "edit") {
    emit("edit", row);
  }
  if (action === "run") {
    await triggerScheduledTask(row.id);
    emit("triggered", row);
  }
  if (action === "logs") {
    emit("logs", row);
  }
}

function resetQuery() {
  query.taskCode = "";
  query.status = "";
  query.handlerCode = "";
  resetPage();
}

load();

defineExpose({load});
</script>

<style scoped>
.query-form {
  flex: 1;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}
</style>
