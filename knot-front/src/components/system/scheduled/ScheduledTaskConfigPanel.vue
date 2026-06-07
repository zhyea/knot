<template>
  <div>
    <el-table v-loading="loading" :data="rows" stripe border size="small">
      <el-table-column prop="taskCode" label="任务编码" min-width="160" show-overflow-tooltip />
      <el-table-column prop="taskName" label="任务名称" min-width="170" show-overflow-tooltip />
      <el-table-column prop="handlerCode" label="处理器" min-width="170" show-overflow-tooltip />
      <el-table-column prop="cronExpression" label="Cron 表达式" width="120" />
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
      <el-table-column prop="lastFireAt" label="上次执行" width="160" align="center" />
      <el-table-column prop="nextFireAt" label="下次执行" width="160" align="center" />
      <el-table-column label="操作" width="120" fixed="right" align="center">
        <template #default="{ row }">
          <RowActions :actions="taskActions(row)" @action="(action) => handleAction(action, row)" />
        </template>
      </el-table-column>
    </el-table>

    <ListPagination
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      :show-refresh="showRefresh"
      @refresh="emit('refresh')"
      @page-change="emit('page-change', $event)"
      @size-change="emit('size-change', $event)"
    />
  </div>
</template>

<script setup>
import { Edit, Tickets, VideoPlay } from "@element-plus/icons-vue";
import RowActions from "../../common/RowActions.vue";
import ListPagination from "../../common/ListPagination.vue";
import { triggerScheduledTask } from "../../../api/scheduledTasks";
import { taskModeLabel, taskStatusLabel } from "../../../utils/scheduledTask";

const props = defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  showRefresh: { type: Boolean, default: true }
});

const emit = defineEmits(["edit", "logs", "triggered", "refresh", "page-change", "size-change"]);

function taskActions(row) {
  return [
    { key: "edit", label: "编辑", icon: Edit },
    { key: "logs", label: "执行记录", icon: Tickets },
    {
      key: "run",
      label: "立即执行",
      icon: VideoPlay,
      disabled: row.status !== "ENABLED",
      confirm: "确认立即执行该任务？"
    }
  ];
}

async function handleAction(action, row) {
  if (action === "edit") {
    emit("edit", row);
    return;
  }
  if (action === "logs") {
    emit("logs", row);
    return;
  }
  if (action === "run") {
    await triggerScheduledTask(row.id);
    emit("triggered", row);
  }
}
</script>
