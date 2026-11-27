<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="onReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按档案编码 / 备注筛选"
          @query="handleQuery"
        />
        <FilterField label="协议" :width="200">
          <el-select v-model="query.protocol" placeholder="全部协议" clearable @change="handleQuery">
            <el-option
              v-for="item in protocolOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </FilterField>
        <FilterField label="状态" :width="140">
          <el-select v-model="query.status" placeholder="全部" clearable @change="handleQuery">
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </FilterField>
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建档案</el-button>
            <el-tooltip
              content="档案是一套「怎么查上游」的口径：新增一家异步上游 = 加一行档案 + 在模型表单里选中它"
              placement="top"
              :show-after="300"
            >
              <el-icon class="profile-help-icon" aria-label="这是什么"><QuestionFilled/></el-icon>
            </el-tooltip>
          </div>
        </div>

        <el-table v-loading="loading" :data="rows" stripe border>
          <el-table-column prop="profileCode" label="档案编码" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">{{ row.profileCode }}</el-button>
              <div v-if="row.remark" class="profile-remark">{{ row.remark }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="protocol" label="协议" min-width="170" show-overflow-tooltip />
          <el-table-column label="查询" min-width="300" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="profile-query">{{ displayText(row.queryMethod) }} {{ displayText(row.queryPath) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="轮询" width="160">
            <template #default="{ row }">
              <div class="profile-poll">
                {{ pollSummary(row) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="产物" width="120" align="center">
            <template #default="{ row }">
              <el-tag v-if="isMultiResult(row)" size="small" type="warning">多产物</el-tag>
              <span v-else>单产物</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <!-- ⚠ status 是数字，0 合法：必须显式比较，不能 truthy -->
              <el-tag :type="row.status === EnabledStatus.ENABLED ? 'success' : 'info'" size="small">
                {{ row.status === EnabledStatus.ENABLED ? "启用" : "停用" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="被引用" width="90" align="center">
            <template #default="{ row }">
              <el-tooltip
                v-if="bindingCountOf(row) > 0"
                content="被模型绑定引用：先改绑或改回同步，才能删除"
                placement="top"
              >
                <el-tag size="small" type="warning">{{ bindingCountOf(row) }}</el-tag>
              </el-tooltip>
              <span v-else>0</span>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" align="center" header-align="center" fixed="right">
            <template #default="{ row }">
              <RowActions
                :actions="[
                  { key: 'edit', label: '编辑', icon: Edit },
                  { key: 'log', label: '操作记录', icon: Document },
                  { key: 'delete', label: '删除', icon: Delete, type: 'danger', disabled: bindingCountOf(row) > 0 }
                ]"
                @action="(action) => handleAction(action, row)"
              />
            </template>
          </el-table-column>
        </el-table>

        <ListPagination
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          @refresh="load"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <AsyncTaskProfileFormDrawer
      v-model="drawer"
      :profile-code="editingCode"
      @saved="load"
    />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`异步任务档案操作记录 - ${logProfileCode || ''}`"
      :load-logs="loadProfileLogs"
    />
  </PageSection>
</template>

<script setup lang="ts">
import {computed, onMounted} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import {Delete, Document, Edit, QuestionFilled} from "@element-plus/icons-vue";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import ListPagination from "../../components/common/ListPagination.vue";
import RowActions from "../../components/common/RowActions.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import AsyncTaskProfileFormDrawer from "../../components/model/async/AsyncTaskProfileFormDrawer.vue";
import {deleteAsyncTaskProfile, listAsyncTaskProfiles} from "@/api/asyncTaskProfiles";
import {listAsyncTaskProfileOperationLogs} from "@/api/operationLogs";
import {useListQuery} from "@/composables/useListQuery";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {useLogDrawer} from "@/composables/useLogDrawer";
import type {Row} from "@/types";
import {displayText, formatDateTime} from "@/utils/format";
import {EnabledStatus} from "@/constants/status";
import {useFormDrawer} from "@/composables/useFormDrawer";
const {id: logProfileId, name: logProfileCode, visible: logDrawer, open: openLog} = useLogDrawer({labelFields: ["profileCode"]});
const {visible: drawer, editing: editingCode, openCreate, openEdit} = useFormDrawer<string>({emptyValue: "", toEditing: (row) => String(row.profileCode ?? "")});

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
  apiFn: listAsyncTaskProfiles,
  fields: {
    keyword: "",
    protocol: "",
    status: ""
  }
});

const {optionsOf} = useEnumOptions();
const protocolOptions = computed(() => optionsOf("ModelApiProtocolEnum"));
const statusOptions = computed(() => optionsOf("EnabledStatusEnum"));


function onReset() {
  handleReset();
}

/** 引用数在「被模型绑定」时不能删：与后端 delete 的占用检查同一口径（后端才是判据） */
function bindingCountOf(row: Row): number {
  return Number(row.bindingCount ?? 0);
}

function isMultiResult(row: Row): boolean {
  return Boolean(row.resultUrlField);
}

/**
 * 轮询摘要：只放人做判断需要的两个数。
 *
 * ⚠ 不展示退避基数/上限：那是排障参数，列表页放四个数字只会让人读不下去。
 */
function pollSummary(row: Row): string {
  const interval = Number(row.pollIntervalMs ?? 0);
  const timeout = Number(row.timeoutMinutes ?? 0);
  const intervalText = interval > 0 ? `${Math.round(interval / 1000)}s` : "默认";
  const timeoutText = timeout > 0 ? `${timeout}min` : "默认";
  return `${intervalText} / ${timeoutText}`;
}

onMounted(load);

function handleAction(action: string, row: Row) {
  if (action === "edit") {
    openEdit(row);
  } else if (action === "log") {
    openLog(row);
  } else if (action === "delete") {
    onDelete(row);
  }
}

/**
 * 操作记录按 entityId 检索：档案的引用键是 profileCode，但日志表只能按主键 id 精确定位
 * （按名字模糊匹配会把 KLING_TASK 与 KLING_TASK2 混在一起）。
 */
function loadProfileLogs() {
  return listAsyncTaskProfileOperationLogs(logProfileId.value!);
}

async function onDelete(row: Row) {
  const profileCode = String(row.profileCode ?? "");
  try {
    await ElMessageBox.confirm(
      `删除档案 ${profileCode} 后不可恢复；已提交的任务仍按提交时的快照跑完。确认删除？`,
      "确认删除",
      {type: "warning"}
    );
  } catch {
    return;
  }
  try {
    await deleteAsyncTaskProfile(profileCode);
    ElMessage.success("已删除");
    load();
  } catch {
    // 被引用 / 不存在：拦截器已提示具体原因
  }
}
</script>

<style scoped>
/* 「这是什么」只留一个问号图标：说明挂在气泡里，不占工具栏位置 */
.profile-help-icon {
  color: var(--el-text-color-secondary);
  font-size: 16px;
  cursor: help;
  outline: none;
}

.profile-help-icon:hover {
  color: var(--el-color-primary);
}

.profile-remark {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.profile-query {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
}

.profile-poll {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
