<template>
  <PageSection>
    <div class="list-page-shell">
      <section class="list-page-block">
        <div class="list-page-filters">
          <div class="list-filter-item operation-log-filter-item">
            <span class="list-filter-label">模块</span>
            <el-select
              v-model="queryForm.module"
              class="list-filter-control"
              placeholder="请选择模块"
              clearable
              filterable
            >
              <el-option
                v-for="item in moduleOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
          <div class="list-filter-item operation-log-filter-item">
            <span class="list-filter-label">操作</span>
            <el-select
              v-model="queryForm.operation"
              class="list-filter-control"
              placeholder="请选择操作"
              clearable
              filterable
            >
              <el-option
                v-for="item in operationOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
          <div class="list-filter-item operation-log-filter-item">
            <span class="list-filter-label">状态</span>
            <el-select
              v-model="queryForm.status"
              class="list-filter-control"
              placeholder="请选择状态"
              clearable
              filterable
            >
              <el-option
                v-for="item in logStatusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
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
import { computed, onMounted, reactive, ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import OperationLogDetailDrawer from "../../components/system/OperationLogDetailDrawer.vue";
import OperationLogListPanel from "../../components/system/OperationLogListPanel.vue";
import { useAutoQuery } from "../../composables/useAutoQuery";
import { usePageList } from "../../composables/usePageList";
import { resolveEnumLabel, useEnums } from "../../composables/useEnums";
import { getOperationLogDetail, listOperationLogs } from "../../api/operationLogs";

const { options: statusOptions, loadOptions: loadStatusOptions } = useEnums("status");

const queryForm = reactive({
  module: "",
  operation: "",
  status: ""
});

const moduleLabelMap = {
  system: "系统管理",
  model: "模型管理",
  routing: "路由管理",
  billing: "计费管理",
  app: "应用管理",
  user: "用户管理",
  department: "部门管理",
  enum: "枚举管理",
  provider: "供应商管理",
  "logical-model": "模型广场"
};

const moduleOptions = ref([]);
const operationOptions = ref([]);
const rawStatusOptions = ref([]);
const detailDrawer = ref(false);
const currentLog = ref(null);

const logStatusOptions = computed(() =>
  rawStatusOptions.value.map((value) => ({
    label: statusLabel(value),
    value
  }))
);

function statusLabel(code) {
  return resolveEnumLabel(statusOptions.value, code, code || "-");
}

async function fetchOperationLogs(params) {
  const result = await listOperationLogs(params);
  moduleOptions.value = normalizeOptions(result?.moduleOptions, resolveModuleLabel);
  operationOptions.value = normalizeOptions(result?.operationOptions);
  rawStatusOptions.value = normalizeValues(result?.statusOptions);
  return result;
}

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(fetchOperationLogs, { extra: queryForm });
const { pauseAutoQuery } = useAutoQuery(queryForm, handleQuery);

function handleQuery() {
  return pauseAutoQuery(() => resetPage());
}

function handleReset() {
  return pauseAutoQuery(() => {
    queryForm.module = "";
    queryForm.operation = "";
    queryForm.status = "";
    return resetPage();
  });
}

async function onLogRow(row) {
  currentLog.value = await getOperationLogDetail(row.id);
  detailDrawer.value = true;
}

function normalizeOptions(values, labelResolver = null) {
  return normalizeValues(values).map((value) => ({
    label: labelResolver ? labelResolver(value) : value,
    value
  }));
}

function resolveModuleLabel(value) {
  return moduleLabelMap[value] || value;
}

function normalizeValues(values) {
  return Array.isArray(values)
    ? values.filter((item) => item != null && `${item}`.trim() !== "").map((item) => `${item}`)
    : [];
}

onMounted(() => {
  loadStatusOptions();
  load();
});
</script>

<style scoped>
.operation-log-filter-item {
  flex: 0 0 260px;
  min-width: 260px;
}

.operation-log-filter-item :deep(.el-select) {
  width: 100%;
}

@media (max-width: 1200px) {
  .operation-log-filter-item {
    flex: 1 1 220px;
    min-width: 220px;
  }
}

@media (max-width: 768px) {
  .operation-log-filter-item {
    flex: 1 1 100%;
    min-width: 0;
  }
}
</style>
