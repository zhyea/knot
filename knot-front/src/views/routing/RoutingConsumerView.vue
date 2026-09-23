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
  </PageSection>
</template>

<script setup>
import { ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import RoutingConsumerFormDrawer from "../../components/routing/RoutingConsumerFormDrawer.vue";
import RoutingConsumerListPanel from "../../components/routing/RoutingConsumerListPanel.vue";
import { useEnabledToggle } from "../../composables/useEnabledToggle";
import { useListQuery } from "../../composables/useListQuery";
import {
  listRoutingConsumers,
  rotateRoutingConsumerSecret,
  updateRoutingConsumerStatus
} from "../../api/routing";

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
const editingConsumer = ref(null);

function openCreate() {
  editingConsumer.value = null;
  drawerVisible.value = true;
}

function openEdit(row) {
  editingConsumer.value = row;
  drawerVisible.value = true;
}

function handleAction(action, row) {
  if (action === "edit") openEdit(row);
  if (action === "rotate") rotateSecret(row);
}

async function handleEnabledChange(row, enabled) {
  await onEnabledChange(row, enabled);
  load();
}

async function rotateSecret(row) {
  await rotateRoutingConsumerSecret(row.id);
  ElMessage.success("API Key 已重置");
  load();
}

async function copySecretKey(secretKey) {
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
