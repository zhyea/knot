<template>
  <PageSection>
    <div class="list-page-shell">
      <section class="list-page-block">
        <div class="list-page-filters">
          <div class="list-filter-item">
            <span class="list-filter-label">模块</span>
            <el-input
              v-model="queryForm.module"
              class="list-filter-control"
              placeholder="请输入模块"
              clearable
              @keyup.enter="handleQuery"
            />
          </div>
          <div class="list-filter-item">
            <span class="list-filter-label">操作</span>
            <el-input
              v-model="queryForm.operation"
              class="list-filter-control"
              placeholder="请输入操作"
              clearable
              @keyup.enter="handleQuery"
            />
          </div>
          <div class="list-filter-item">
            <span class="list-filter-label">状态</span>
            <EnumSelect
              v-model="queryForm.status"
              category="status"
              :include-codes="['SUCCESS', 'FAILURE']"
              class="list-filter-control"
              placeholder="请选择状态"
              clearable
            />
          </div>
          <div class="list-filter-actions">
            <el-button type="primary" @click="handleQuery">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </div>
        </div>
      </section>

      <section class="list-page-block list-page-block--content">
        <OperationLogListPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :status-label="statusLabel"
          :show-refresh="false"
          @row-click="onLogRow"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <OperationLogDetailDrawer
      v-model="detailDrawer"
      :log="currentLog"
      :status-label="statusLabel"
    />
  </PageSection>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import EnumSelect from "../../components/common/EnumSelect.vue";
import OperationLogDetailDrawer from "../../components/system/OperationLogDetailDrawer.vue";
import OperationLogListPanel from "../../components/system/OperationLogListPanel.vue";
import { usePageList } from "../../composables/usePageList";
import { resolveEnumLabel, useEnums } from "../../composables/useEnums";
import { getOperationLogDetail, listOperationLogs } from "../../api/operationLogs";

const { options: statusOptions, loadOptions: loadStatusOptions } = useEnums("status");

function statusLabel(code) {
  return resolveEnumLabel(statusOptions.value, code, code || "-");
}

onMounted(() => loadStatusOptions());

const queryForm = reactive({
  module: "",
  operation: "",
  status: ""
});

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listOperationLogs, { extra: queryForm });

const detailDrawer = ref(false);
const currentLog = ref(null);

function handleQuery() {
  return resetPage();
}

function handleReset() {
  queryForm.module = "";
  queryForm.operation = "";
  queryForm.status = "";
  return resetPage();
}

async function onLogRow(row) {
  currentLog.value = await getOperationLogDetail(row.id);
  detailDrawer.value = true;
}

load();
</script>
