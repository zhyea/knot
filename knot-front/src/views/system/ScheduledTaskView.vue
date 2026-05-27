<template>
  <PageSection title="定时任务">
    <el-tabs v-model="activeTab" class="task-tabs">
      <el-tab-pane label="任务配置" name="tasks">
        <div class="toolbar">
          <el-form :inline="true" :model="taskQuery" class="query-form">
            <el-form-item label="任务编码">
              <el-input v-model="taskQuery.taskCode" placeholder="请输入任务编码" clearable />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="taskQuery.status" placeholder="请选择状态" clearable>
                <el-option label="启用" value="ENABLED" />
                <el-option label="禁用" value="DISABLED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="searchTasks">查询</el-button>
              <el-button @click="resetTaskQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <div class="toolbar-actions">
            <el-button type="primary" @click="openTaskDrawer()">新增</el-button>
            <el-button @click="loadTasks">刷新</el-button>
          </div>
        </div>

        <el-table v-loading="taskLoading" :data="taskRows" stripe border size="small">
          <el-table-column prop="taskCode" label="任务编码" min-width="180" show-overflow-tooltip />
          <el-table-column prop="taskName" label="任务名称" min-width="170" show-overflow-tooltip />
          <el-table-column prop="handlerCode" label="处理器" min-width="190" show-overflow-tooltip />
          <el-table-column prop="cronExpression" label="Cron" width="140" />
          <el-table-column prop="executionMode" label="执行模式" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="row.executionMode === 'SINGLE' ? 'warning' : 'success'" size="small">
                {{ modeLabel(row.executionMode) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastFireAt" label="上次执行" width="180" />
          <el-table-column prop="nextFireAt" label="下次执行" width="180" />
          <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
          <el-table-column label="操作" width="120" fixed="right" align="center">
            <template #default="{ row }">
              <RowActions
                :actions="taskActions(row)"
                @action="(action) => handleTaskAction(action, row)"
              />
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-wrap">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="taskTotal"
            :page-size="taskPageSize"
            :current-page="taskPageNum"
            :page-sizes="[10, 20, 50]"
            @current-change="onTaskPageChange"
            @size-change="onTaskSizeChange"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="执行记录" name="runs">
        <div class="toolbar">
          <el-form :inline="true" :model="runQuery" class="query-form">
            <el-form-item label="任务编码">
              <el-input v-model="runQuery.taskCode" placeholder="请输入任务编码" clearable />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="runQuery.status" placeholder="请选择状态" clearable>
                <el-option label="运行中" value="RUNNING" />
                <el-option label="成功" value="SUCCESS" />
                <el-option label="失败" value="FAILURE" />
              </el-select>
            </el-form-item>
            <el-form-item label="触发类型">
              <el-select v-model="runQuery.triggerType" placeholder="请选择触发类型" clearable>
                <el-option label="调度" value="SCHEDULE" />
                <el-option label="手动" value="MANUAL" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="searchRuns">查询</el-button>
              <el-button @click="resetRunQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <el-button @click="loadRuns">刷新</el-button>
        </div>
        <el-table v-loading="runLoading" :data="runRows" stripe border size="small">
          <el-table-column prop="taskCode" label="任务编码" min-width="170" show-overflow-tooltip />
          <el-table-column prop="taskName" label="任务名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="nodeId" label="执行节点" min-width="170" show-overflow-tooltip />
          <el-table-column prop="executionMode" label="执行模式" width="100" align="center">
            <template #default="{ row }">{{ modeLabel(row.executionMode) }}</template>
          </el-table-column>
          <el-table-column prop="triggerType" label="触发类型" width="100" align="center">
            <template #default="{ row }">{{ triggerLabel(row.triggerType) }}</template>
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
            :total="runTotal"
            :page-size="runPageSize"
            :current-page="runPageNum"
            :page-sizes="[10, 20, 50]"
            @current-change="onRunPageChange"
            @size-change="onRunSizeChange"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="taskDrawer" :title="editingTask?.id ? '编辑任务配置' : '新增任务配置'" size="520px">
      <el-form ref="taskFormRef" :model="taskForm" :rules="taskRules" label-width="110px">
        <el-form-item label="任务编码" prop="taskCode">
          <el-input v-model="taskForm.taskCode" placeholder="如 operation-log-retention" :disabled="Boolean(editingTask?.id)" />
        </el-form-item>
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="taskForm.taskName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="处理器" prop="handlerCode">
          <el-select v-model="taskForm.handlerCode" placeholder="请选择处理器" filterable>
            <el-option v-for="code in handlerOptions" :key="code" :label="code" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron" prop="cronExpression">
          <el-input v-model="taskForm.cronExpression" placeholder="如 0 0 3 * * ?" />
        </el-form-item>
        <el-form-item label="执行模式" prop="executionMode">
          <el-radio-group v-model="taskForm.executionMode">
            <el-radio-button label="SINGLE">单节点</el-radio-button>
            <el-radio-button label="BROADCAST">广播</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="taskForm.status">
            <el-radio-button label="ENABLED">启用</el-radio-button>
            <el-radio-button label="DISABLED">禁用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="taskForm.description" type="textarea" :rows="3" placeholder="请输入说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDrawer = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTask">保存</el-button>
      </template>
    </el-drawer>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { Edit, VideoPlay } from "@element-plus/icons-vue";
import PageSection from "../../components/common/PageSection.vue";
import RowActions from "../../components/common/RowActions.vue";
import { usePageList } from "../../composables/usePageList";
import {
  createScheduledTask,
  listScheduledTaskHandlers,
  listScheduledTaskRuns,
  listScheduledTasks,
  triggerScheduledTask,
  updateScheduledTask
} from "../../api/scheduledTasks";

const activeTab = ref("tasks");
const taskQuery = reactive({ taskCode: "", status: "", handlerCode: "" });
const runQuery = reactive({ taskCode: "", status: "", triggerType: "" });

const {
  rows: taskRows,
  loading: taskLoading,
  total: taskTotal,
  pageNum: taskPageNum,
  pageSize: taskPageSize,
  load: loadTasks,
  resetPage: resetTaskPage,
  onPageChange: onTaskPageChange,
  onSizeChange: onTaskSizeChange
} = usePageList(listScheduledTasks, { extra: taskQuery });

const {
  rows: runRows,
  loading: runLoading,
  total: runTotal,
  pageNum: runPageNum,
  pageSize: runPageSize,
  load: loadRuns,
  resetPage: resetRunPage,
  onPageChange: onRunPageChange,
  onSizeChange: onRunSizeChange
} = usePageList(listScheduledTaskRuns, { extra: runQuery });

const taskDrawer = ref(false);
const saving = ref(false);
const editingTask = ref(null);
const taskFormRef = ref();
const handlerOptions = ref([]);
const taskForm = reactive({
  taskCode: "",
  taskName: "",
  handlerCode: "",
  cronExpression: "",
  executionMode: "SINGLE",
  status: "ENABLED",
  description: ""
});

const taskRules = {
  taskCode: [{ required: true, message: "请输入任务编码", trigger: "blur" }],
  taskName: [{ required: true, message: "请输入任务名称", trigger: "blur" }],
  handlerCode: [{ required: true, message: "请选择处理器", trigger: "change" }],
  cronExpression: [{ required: true, message: "请输入 Cron 表达式", trigger: "blur" }],
  executionMode: [{ required: true, message: "请选择执行模式", trigger: "change" }],
  status: [{ required: true, message: "请选择状态", trigger: "change" }]
};

function modeLabel(value) {
  return value === "BROADCAST" ? "广播" : "单节点";
}

function statusLabel(value) {
  return value === "ENABLED" ? "启用" : "禁用";
}

function triggerLabel(value) {
  return value === "MANUAL" ? "手动" : "调度";
}

function runStatusLabel(value) {
  const map = { RUNNING: "运行中", SUCCESS: "成功", FAILURE: "失败" };
  return map[value] || value || "-";
}

function runStatusType(value) {
  if (value === "SUCCESS") return "success";
  if (value === "FAILURE") return "danger";
  return "warning";
}

function taskActions(row) {
  return [
    { key: "edit", label: "编辑", icon: Edit },
    { key: "run", label: "立即执行", icon: VideoPlay, disabled: row.status !== "ENABLED", confirm: "确认立即执行该任务？" }
  ];
}

async function handleTaskAction(action, row) {
  if (action === "edit") {
    openTaskDrawer(row);
  }
  if (action === "run") {
    await triggerScheduledTask(row.id);
    ElMessage.success("已提交执行");
    activeTab.value = "runs";
    runQuery.taskCode = row.taskCode;
    resetRunPage();
  }
}

async function loadHandlers() {
  handlerOptions.value = await listScheduledTaskHandlers();
}

function openTaskDrawer(row) {
  editingTask.value = row || null;
  Object.assign(taskForm, {
    taskCode: row?.taskCode || "",
    taskName: row?.taskName || "",
    handlerCode: row?.handlerCode || "",
    cronExpression: row?.cronExpression || "",
    executionMode: row?.executionMode || "SINGLE",
    status: row?.status || "ENABLED",
    description: row?.description || ""
  });
  taskDrawer.value = true;
}

async function saveTask() {
  await taskFormRef.value?.validate();
  saving.value = true;
  try {
    if (editingTask.value?.id) {
      await updateScheduledTask(editingTask.value.id, taskForm);
    } else {
      await createScheduledTask(taskForm);
    }
    ElMessage.success("保存成功");
    taskDrawer.value = false;
    loadTasks();
  } finally {
    saving.value = false;
  }
}

function searchTasks() {
  resetTaskPage();
}

function resetTaskQuery() {
  taskQuery.taskCode = "";
  taskQuery.status = "";
  taskQuery.handlerCode = "";
  resetTaskPage();
}

function searchRuns() {
  resetRunPage();
}

function resetRunQuery() {
  runQuery.taskCode = "";
  runQuery.status = "";
  runQuery.triggerType = "";
  resetRunPage();
}

loadHandlers();
loadTasks();
loadRuns();
</script>

<style scoped>
.task-tabs {
  background: var(--knot-surface, #fff);
  border: 1px solid var(--knot-border, #dcdfe6);
  padding: 16px;
}

.query-form {
  flex: 1;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}
</style>
