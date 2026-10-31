<template>
  <div>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="taskCode" label="任务句柄" min-width="210" show-overflow-tooltip>
        <template #default="{ row }">
          <el-button link type="primary" @click="emit('view', row)">{{ row.taskCode }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="96" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="profileCode" label="任务档案" min-width="150" show-overflow-tooltip />
      <el-table-column prop="protocol" label="协议" min-width="150" show-overflow-tooltip />
      <el-table-column prop="upstreamTaskId" label="上游任务号" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ displayText(row.upstreamTaskId) }}</template>
      </el-table-column>
      <el-table-column label="查询次数" width="100" align="center">
        <template #default="{ row }">{{ row.attempts ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="轮次" width="80" align="center">
        <template #default="{ row }">
          <!-- 轮次 > 0 才标出来：正常任务恒为 0，加色会让人以为「重试过」是常态 -->
          <el-tag v-if="(row.retryRound ?? 0) > 0" type="warning" size="small">{{ row.retryRound }}</el-tag>
          <span v-else>0</span>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
      </el-table-column>
      <el-table-column label="完成时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.completedAt) }}</template>
      </el-table-column>
      <el-table-column label="错误码" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">{{ displayText(row.errorCode) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions :actions="actionsOf(row)" @action="(action) => emit('action', action, row)" />
        </template>
      </el-table-column>
    </el-table>

    <ListPagination
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      :show-refresh="showRefresh"
      @refresh="emit('refresh')"
      @page-change="(page) => emit('page-change', page)"
      @size-change="(size) => emit('size-change', size)"
    />
  </div>
</template>

<script setup lang="ts">
import {type PropType} from "vue";
import {CircleClose, RefreshRight, Search} from "@element-plus/icons-vue";
import type {Row, RowAction} from "@/types";
import ListPagination from "../../common/ListPagination.vue";
import RowActions from "../../common/RowActions.vue";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {AsyncTaskStatus} from "@/constants/status";
import {displayText, formatDateTime} from "@/utils/format";

defineProps({
  rows: {type: Array as PropType<Row[]>, default: (): Row[] => []},
  loading: {type: Boolean, default: false},
  total: {type: Number, default: 0},
  pageNum: {type: Number, default: 1},
  pageSize: {type: Number, default: 20},
  showRefresh: {type: Boolean, default: true}
});

const emit = defineEmits(["view", "action", "refresh", "page-change", "size-change"]);

const {labelOf} = useEnumOptions();

function statusLabel(status: unknown): string {
  return labelOf("AsyncTaskStatusEnum", status);
}

/**
 * 状态 → 标签色。
 *
 * ⚠ 用 `===` 与常量比较，不用 `switch` 之外的花写法：status 是数字，0 是合法值，
 * 任何 truthy 判断都会把 PENDING 当成「无状态」。
 */
function statusTagType(status: unknown): "success" | "warning" | "danger" | "info" {
  if (status === AsyncTaskStatus.SUCCEEDED) {
    return "success";
  }
  if (status === AsyncTaskStatus.POLLING) {
    return "warning";
  }
  if (status === AsyncTaskStatus.FAILED) {
    return "danger";
  }
  return "info";
}

/**
 * 行操作按状态显隐：
 * - 终止：只有在途（0/1）才有意义。终态任务再点终止会得到「已是终态」的提示，
 *   那是噪音，不如直接不显示；
 * - 重试：只有失败 / 超时（3/4）—— 与后端 `AsyncTaskStatusEnum#retryable()` 是同一份口径。
 *   已成功的任务重开会二次计费，已人工终止的重开会推翻人的判断。
 *
 * ⚠ 这里的显隐只是<b>界面提示</b>，真正的判据在后端（网关的 `AsyncTaskAdminRunner`）：
 * 前端隐藏按钮不等于权限或状态校验，直接调接口照样会被后端拒掉。
 */
function actionsOf(row: Row): RowAction[] {
  const status = row.status;
  const inFlight = status === AsyncTaskStatus.PENDING || status === AsyncTaskStatus.POLLING;
  const retryable = status === AsyncTaskStatus.FAILED || status === AsyncTaskStatus.EXPIRED;
  return [
    {key: "view", label: "查看", icon: Search},
    {
      key: "cancel",
      label: "终止",
      icon: CircleClose,
      type: "warning",
      hidden: !inFlight,
      confirm: "终止后该任务不再轮询、不计费，且不可恢复。确认终止？"
    },
    {
      key: "retry",
      label: "重试",
      icon: RefreshRight,
      hidden: !retryable,
      confirm: "重试会重新轮询<同一笔上游任务>（不会重新提交），确认继续？"
    }
  ];
}
</script>
