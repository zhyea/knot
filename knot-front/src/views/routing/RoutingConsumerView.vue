<template>
  <PageSection>
    <div class="list-page-shell">
      <section class="list-page-block">
        <div class="list-page-filters">
          <div class="list-filter-item list-filter-item--grow">
            <span class="list-filter-label">关键词</span>
            <el-input
              v-model="query.keyword"
              class="list-filter-control--wide"
              placeholder="按消费者编码、名称、用户筛选"
              clearable
              @keyup.enter="handleQuery"
            />
          </div>
          <div class="list-filter-actions">
            <el-button type="primary" @click="handleQuery">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </div>
        </div>
      </section>

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
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import RoutingConsumerFormDrawer from "../../components/routing/RoutingConsumerFormDrawer.vue";
import RoutingConsumerListPanel from "../../components/routing/RoutingConsumerListPanel.vue";
import { useAutoQuery } from "../../composables/useAutoQuery";
import { usePageList } from "../../composables/usePageList";
import { useEnabledToggle } from "../../composables/useEnabledToggle";
import {
  listRoutingConsumers,
  rotateRoutingConsumerSecret,
  updateRoutingConsumerStatus
} from "../../api/routing";

const query = reactive({
  keyword: ""
});

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listRoutingConsumers, { extra: query });
const { pauseAutoQuery } = useAutoQuery(query, handleQuery);

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

function handleQuery() {
  return pauseAutoQuery(() => resetPage());
}

function handleReset() {
  return pauseAutoQuery(() => {
    query.keyword = "";
    return resetPage();
  });
}

load();
</script>
