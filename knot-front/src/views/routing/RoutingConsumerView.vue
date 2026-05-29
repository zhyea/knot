<template>
  <PageSection title="消费者">
    <RoutingConsumerListPanel
      :rows="rows"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      :toggling-id="togglingId"
      @create="openCreate"
      @refresh="load"
      @action="handleAction"
      @enabled-change="handleEnabledChange"
      @copy-secret="copySecretKey"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    />

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
import RoutingConsumerFormDrawer from "../../components/routing/RoutingConsumerFormDrawer.vue";
import RoutingConsumerListPanel from "../../components/routing/RoutingConsumerListPanel.vue";
import { usePageList } from "../../composables/usePageList";
import { useEnabledToggle } from "../../composables/useEnabledToggle";
import {
  listRoutingConsumers,
  rotateRoutingConsumerSecret,
  updateRoutingConsumer
} from "../../api/routing";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listRoutingConsumers);

const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateRoutingConsumer,
  buildPayload: (row, enabled) => ({
    consumerCode: row.consumerCode,
    name: row.name,
    userId: row.userId ?? null,
    returnUsageDetail: row.returnUsageDetail === true,
    enabled,
    rateLimitPolicy: row.rateLimitPolicy ?? null,
    quotaPolicy: row.quotaPolicy ?? null
  })
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
