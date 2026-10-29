<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="onReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按任务句柄 / 上游任务号筛选"
          @query="handleQuery"
        />
        <FilterField label="状态" :width="180">
          <el-select v-model="query.status" placeholder="全部状态" clearable>
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </FilterField>
        <FilterField label="任务档案" :width="200">
          <el-input v-model="query.profileCode" placeholder="档案编码，如 KLING_TASK" clearable />
        </FilterField>
        <FilterField label="协议" :width="200">
          <el-input v-model="query.protocol" placeholder="协议编码" clearable />
        </FilterField>
        <FilterField label="提交时间" :width="360">
          <el-date-picker
            v-model="submittedRange"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            range-separator="至"
            clearable
            @change="onRangeChange"
          />
        </FilterField>
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-tooltip
              content="立即按配置的保留期清理已终态的任务行（不删在途任务）"
              placement="top"
              :show-after="300"
            >
              <el-button :loading="cleaning" @click="onCleanup">清理终态任务</el-button>
            </el-tooltip>
          </div>
        </div>

        <AsyncTaskListPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :show-refresh="false"
          @view="openDetail"
          @action="onRowAction"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <AsyncTaskDetailDrawer
      v-model="detailDrawer"
      :task-code="detailCode"
      @changed="load"
    />
  </PageSection>
</template>

<script setup lang="ts">
import {computed, ref} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import AsyncTaskListPanel from "../../components/routing/async/AsyncTaskListPanel.vue";
import AsyncTaskDetailDrawer from "../../components/routing/async/AsyncTaskDetailDrawer.vue";
import {cleanupAsyncTasks, listAsyncTasks, cancelAsyncTask, retryAsyncTask} from "@/api/asyncTasks";
import {useListQuery} from "@/composables/useListQuery";
import {useEnumOptions} from "@/composables/useEnumOptions";
import type {Row} from "@/types";

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
  handleQuery,
  handleReset
} = useListQuery({
  apiFn: listAsyncTasks,
  fields: {
    keyword: "",
    status: "",
    profileCode: "",
    protocol: "",
    submittedFrom: "",
    submittedTo: ""
  }
});

const {optionsOf} = useEnumOptions();
const statusOptions = computed(() => optionsOf("AsyncTaskStatusEnum"));

/** 时间区间单独持有：el-date-picker 的 v-model 是数组，而请求体要两个扁平字段 */
const submittedRange = ref<[string, string] | null>(null);

const detailDrawer = ref(false);
const detailCode = ref("");
const cleaning = ref(false);

function onRangeChange(value: [string, string] | null) {
  query.submittedFrom = value?.[0] ?? "";
  query.submittedTo = value?.[1] ?? "";
  handleQuery();
}

/**
 * 重置时同时清掉日期控件：`handleReset` 只清 `fields` 里声明的字段，
 * 日期控件绑的是本地 ref，不清就会「条件已重置但界面上还显示着区间」。
 */
function onReset() {
  submittedRange.value = null;
  handleReset();
}

function openDetail(row: Row) {
  detailCode.value = String(row.taskCode ?? "");
  detailDrawer.value = true;
}

/**
 * 行操作：终止 / 重试。
 *
 * ⚠ 与详情抽屉里的同一组动作<b>共用同一套判断</b>：后端用 `200 + changed=false` 表达
 * 「没做成但也不是错误」（已是终态、正被轮询节点处理），所以这里必须读 `changed`，
 * 不能只看请求是否成功——否则运维会以为操作生效了。
 */
async function onRowAction(action: string, row: Row) {
  const taskCode = String(row.taskCode ?? "");
  if (!taskCode) {
    return;
  }
  if (action === "view") {
    openDetail(row);
    return;
  }
  const isCancel = action === "cancel";
  try {
    await ElMessageBox.confirm(
      isCancel
        ? "终止后该任务不再轮询、不计费，且不可恢复。确认终止？"
        : "重试会重新轮询同一笔上游任务（不会重新提交），确认继续？",
      isCancel ? "确认终止" : "确认重试",
      {type: "warning"}
    );
  } catch {
    return;
  }
  try {
    const result = (await (isCancel ? cancelAsyncTask(taskCode) : retryAsyncTask(taskCode))) as Record<string, unknown>;
    const message = String(result?.message ?? "");
    if (result?.changed === true) {
      ElMessage.success(message || "操作成功");
    } else {
      ElMessage.warning(message || "操作未生效");
    }
    load();
  } catch {
    // 网关不可用 / 任务不存在：拦截器已提示，无需额外刷新
  }
}

/**
 * 清理终态任务。
 *
 * <p>⚠ 这条操作<b>不可撤销</b>（直接 DELETE 已终态行），所以确认文案里要写清
 * 「只删终态」——否则运维会以为连在途任务一起清了。</p>
 */
async function onCleanup() {
  try {
    await ElMessageBox.confirm(
      "将立即删除已过保留期的终态任务行（在途任务不受影响），删除后不可恢复。确认继续？",
      "确认清理",
      {type: "warning"}
    );
  } catch {
    return;
  }
  cleaning.value = true;
  try {
    const removed = await cleanupAsyncTasks();
    ElMessage.success(`已清理 ${removed ?? 0} 条终态任务`);
    load();
  } catch {
    // 网关不可用：拦截器已提示
  } finally {
    cleaning.value = false;
  }
}
</script>
