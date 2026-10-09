<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按编码、名称、类型筛选"
          @query="handleQuery"
        />
        <FilterField label="状态" :width="180">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option
            v-for="item in pluginStatusOptions"
            :key="item.itemCode"
            :label="item.itemLabel"
            :value="item.itemCode"
            />
          </el-select>
        </FilterField>
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="dlg = true">新建插件</el-button>
          </div>
        </div>

        <PluginListPanel
          :rows="pluginRows"
          :loading="loading"
          :status-updating-id="statusUpdatingId"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :show-refresh="false"
          @status-change="onStatus"
          @action="onPluginAction"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <PluginFormDialog v-model="dlg" @saved="resetPage" />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`插件操作日志 - ${logName || ''}`"
      :load-logs="loadPluginLogs"
    />
  </PageSection>
</template>

<script setup lang="ts">
import type {Row} from "@/types";
import {computed, onMounted, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import {useListQuery} from "@/composables/useListQuery";
import PluginFormDialog from "../../components/plugin/PluginFormDialog.vue";
import PluginListPanel from "../../components/plugin/PluginListPanel.vue";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {listPlugins, updatePluginStatus} from "@/api/plugins";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import {listOperationLogsByEntity} from "@/api/operationLogs";

// 插件状态筛选：PluginInstanceStatusEnum（代码枚举，/api/common/enums；1-草稿 2-生效 3-暂停 4-归档）
const {optionsOf: enumOptionsOf} = useEnumOptions();
const pluginStatusOptions = computed(() =>
  enumOptionsOf("PluginInstanceStatusEnum").map((item) => ({
    itemCode: item.value,
    itemLabel: item.label
  }))
);

const {
  query,
  rows,
  loading,
  total,
  pageNum,
  pageSize,
  load: pageLoad,
  onPageChange,
  onSizeChange,
  resetPage,
  handleQuery,
  handleReset
} = useListQuery({ apiFn: listPlugins, fields: { keyword: "", status: "" } });

const pluginRows = ref<Row[]>([]);
const dlg = ref(false);
const statusUpdatingId = ref<number | null>(null);

const logDrawer = ref(false);
const logId = ref<number | string | null>(null);
const logName = ref("");

function onPluginAction(action: string, row: Row) {
  if (action === "log") {
    logId.value = row.id;
    logName.value = row.name || row.code || `#${row.id}`;
    logDrawer.value = true;
  }
}

function loadPluginLogs() {
  return listOperationLogsByEntity({ module: "plugin", entityType: "Plugin", entityId: logId.value! });
}

watch(
  rows,
  (list) => {
    pluginRows.value = list.map((row) => ({ ...row }));
  },
  { immediate: true }
);

async function onStatus(row: Row, enabled: string | number | boolean) {
  if (!row?.id) {
    return;
  }
  const previousStatus = row.status;
  const nextStatus = enabled ? "ENABLED" : "DISABLED";
  if (previousStatus === nextStatus) {
    return;
  }
  statusUpdatingId.value = row.id;
  row.status = nextStatus;
  try {
    await updatePluginStatus(row.id, { status: nextStatus });
    ElMessage.success("已更新");
    await resetPage();
  } catch {
    row.status = previousStatus;
  } finally {
    statusUpdatingId.value = null;
  }
}


onMounted(() => {
  pageLoad();
});
</script>
