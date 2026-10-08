<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按消费者编码、名称、用户筛选"
          @query="handleQuery"
        />
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建消费者</el-button>
          </div>
        </div>

        <RoutingConsumerListPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :toggling-id="togglingId"
          :show-refresh="false"
          @action="handleAction"
          @enabled-change="handleEnabledChange"
          @copy-secret="copySecretKey"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <RoutingConsumerFormDrawer
      v-model="drawerVisible"
      :consumer="editingConsumer"
      @saved="resetPage"
    />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`消费者操作记录 - ${logConsumerName || ''}`"
      :load-logs="loadRoutingConsumerOperationLogs"
    />
  </PageSection>
</template>

<script setup lang="ts">
import type {Dict, Row} from "@/types";
import {ref} from "vue";
import {ElMessage} from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import RoutingConsumerFormDrawer from "../../components/routing/RoutingConsumerFormDrawer.vue";
import RoutingConsumerListPanel from "../../components/routing/RoutingConsumerListPanel.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import {useEnabledToggle} from "@/composables/useEnabledToggle";
import {useListQuery} from "@/composables/useListQuery";
import {
  listRoutingConsumers,
  rotateRoutingConsumerSecret,
  updateRoutingConsumerStatus
} from "@/api/routing";
import {listRoutingConsumerOperationLogs} from "@/api/operationLogs";

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
  resetPage,
  handleQuery,
  handleReset
} = useListQuery({ apiFn: listRoutingConsumers, fields: { keyword: "" } });

const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateRoutingConsumerStatus
});

const drawerVisible = ref(false);
const editingConsumer = ref<Dict | null>(null);
const logDrawer = ref(false);
const logConsumerId = ref<number | string | null>(null);
const logConsumerName = ref("");

function openCreate() {
  editingConsumer.value = null;
  drawerVisible.value = true;
}

function openEdit(row: Row) {
  editingConsumer.value = row;
  drawerVisible.value = true;
}

function handleAction(action: string, row: Row) {
  if (action === "edit") openEdit(row);
  if (action === "rotate") rotateSecret(row);
  if (action === "log") openLog(row);
}

function openLog(row: Row) {
  logConsumerId.value = row.id;
  logConsumerName.value = row.name || row.consumerCode || `#${row.id}`;
  logDrawer.value = true;
}

function loadRoutingConsumerOperationLogs() {
  return listRoutingConsumerOperationLogs(logConsumerId.value!);
}

async function handleEnabledChange(row: Row, enabled: string | number | boolean) {
  await onEnabledChange(row, enabled);
  load();
}

async function rotateSecret(row: Row) {
  await rotateRoutingConsumerSecret(row.id);
  ElMessage.success("API Key 已重置");
  load();
}

async function copySecretKey(secretKey: string | null | undefined) {
  if (!secretKey) return;
  try {
    await navigator.clipboard.writeText(secretKey);
    ElMessage.success("已复制 API Key");
  } catch {
    ElMessage.error("复制失败");
  }
}


load();
</script>
