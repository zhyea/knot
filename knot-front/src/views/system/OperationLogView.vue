<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按模块、操作、对象、操作人筛选"
          @query="handleQuery"
        />
        <FilterField label="模块" :width="180">
          <el-select
            v-model="query.module"
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
        </FilterField>
        <FilterField label="操作" :width="180">
          <el-select
            v-model="query.operation"
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
        </FilterField>
        <FilterField label="状态" :width="180">
          <el-select
            v-model="query.status"
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
        </FilterField>
      </FilterBar>

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

<script setup lang="ts">
import type {Dict, Row} from "@/types";
import {computed, onMounted, ref} from "vue";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import OperationLogDetailDrawer from "../../components/system/OperationLogDetailDrawer.vue";
import OperationLogListPanel from "../../components/system/OperationLogListPanel.vue";
import {useListQuery} from "@/composables/useListQuery";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {getOperationLogDetail, listOperationLogs} from "@/api/operationLogs";

const { labelOf } = useEnumOptions();

const moduleLabelMap = {
  system: "系统管理",
  model: "模型管理",
  routing: "路由管理",
  billing: "计费管理",
  app: "应用管理",
  user: "用户管理",
  department: "部门管理",
  enum: "枚举管理",
  provider: "供应商",
  authorization: "授权管理",
  auth: "认证",
  "logical-model": "统一模型",
  "model-pool": "模型池",
  "model-family": "模型族",
  "external-model": "外部模型",
  "provider-account": "供应商账户",
  "provider-profile": "供应商档案",
  "scheduled-task": "定时任务",
  notification: "通知",
  plugin: "插件",
  "user-setting": "个人设置"
};

const moduleOptions = ref<Row[]>([]);
const operationOptions = ref<Row[]>([]);
const rawStatusOptions = ref<number[]>([]);
const detailDrawer = ref(false);
const currentLog = ref<Dict | null>(null);

const logStatusOptions = computed(() =>
  rawStatusOptions.value.map((value) => ({
    label: statusLabel(value),
    value
  }))
);

function statusLabel(code: number) {
  return labelOf("OperationLogStatusEnum", code, String(code ?? "-"));
}

async function fetchOperationLogs(params: Dict) {
  const result = await listOperationLogs(params);
  moduleOptions.value = normalizeOptions(result?.moduleOptions, resolveModuleLabel);
  operationOptions.value = normalizeOptions(result?.operationOptions);
  rawStatusOptions.value = normalizeNumberValues(result?.statusOptions);
  return result;
}

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
  apiFn: fetchOperationLogs,
  fields: { keyword: "", module: "", operation: "", status: null as number | null }
});

async function onLogRow(row: Row) {
  currentLog.value = await getOperationLogDetail(row.id);
  detailDrawer.value = true;
}

function normalizeOptions(values: unknown, labelResolver: ((value: string) => string) | null = null) {
  return normalizeValues(values).map((value) => ({
    label: labelResolver ? labelResolver(value) : value,
    value
  }));
}

function resolveModuleLabel(value: string): string {
  return (moduleLabelMap as Dict)[value] || value;
}

function normalizeValues(values: unknown): string[] {
  return Array.isArray(values)
    ? values.filter((item) => item != null && `${item}`.trim() !== "").map((item) => `${item}`)
    : [];
}

function normalizeNumberValues(values: unknown): number[] {
  return Array.isArray(values) ? values.filter((item): item is number => typeof item === "number") : [];
}

onMounted(() => {
  load();
});
</script>
