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
          @copy-secret="copy"
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
import type {Row} from "@/types";

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
import {useCopyToClipboard} from "@/composables/useCopyToClipboard";
import {useLogDrawer} from "@/composables/useLogDrawer";
import {useRowFormDrawer} from "@/composables/useFormDrawer";
const {id: logConsumerId, name: logConsumerName, visible: logDrawer, open: openLog} = useLogDrawer({labelFields: ["name", "consumerCode"]});
const {visible: drawerVisible, editing: editingConsumer, openCreate, openEdit} = useRowFormDrawer();

const {copy} = useCopyToClipboard({successMessage: "已复制 API Key"});

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

const {togglingId, handleEnabledChange} = useEnabledToggle({
  updateApi: updateRoutingConsumerStatus,
  onChanged: () => load()
});


function handleAction(action: string, row: Row) {
  if (action === "edit") openEdit(row);
  if (action === "rotate") rotateSecret(row);
  if (action === "log") openLog(row);
}

function loadRoutingConsumerOperationLogs() {
  return listRoutingConsumerOperationLogs(logConsumerId.value!);
}

async function rotateSecret(row: Row) {
  await rotateRoutingConsumerSecret(row.id);
  ElMessage.success("API Key 已重置");
  load();
}

load();
</script>
