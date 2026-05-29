<template>
  <PageSection class="market-page" title="模型广场">
    <LogicalModelMarketplacePanel
      ref="marketPanelRef"
      :rows="rows"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      :grid-style="gridStyle"
      :model-type-options="modelTypeOptions"
      @refresh="load"
      @create="openCreate"
      @action="handleAction"
      @page-change="onPageChange"
    />

    <LogicalModelFormDrawer v-model="formVisible" :model="editingModel" @saved="resetPage" />
  </PageSection>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import LogicalModelFormDrawer from "../components/model/LogicalModelFormDrawer.vue";
import LogicalModelMarketplacePanel from "../components/model/LogicalModelMarketplacePanel.vue";
import { deleteLogicalModel, listLogicalModels } from "../api/logicalModels";
import { useEnums } from "../composables/useEnums";
import { usePageList } from "../composables/usePageList";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, resetPage } =
  usePageList(listLogicalModels, { pageSize: 9 });

const { options: modelTypeOptions, loadOptions: loadModelTypes } = useEnums("model_type");
const marketPanelRef = ref(null);
const formVisible = ref(false);
const editingModel = ref(null);
const layout = reactive({
  columns: 3,
  rows: 3,
  gridHeight: 0,
  cardHeight: 210
});

const GAP_X = 22;
const GAP_Y = 18;
const MIN_CARD_WIDTH = 325;
const MIN_CARD_HEIGHT = 168;
const MAX_COLUMNS = 5;
const MAX_ROWS = 5;
const LAYOUT_SAFETY_GAP = 2;
let resizeObserver;
let resizeTimer;
let loaded = false;

const gridStyle = computed(() => ({
  "--market-columns": layout.columns,
  "--market-rows": layout.rows,
  "--market-grid-height": layout.gridHeight ? `${layout.gridHeight}px` : "auto",
  "--market-card-height": `${layout.cardHeight}px`
}));

function openCreate() {
  editingModel.value = null;
  formVisible.value = true;
}

function openEdit(row) {
  editingModel.value = row;
  formVisible.value = true;
}

function handleAction(action, row) {
  if (action === "edit") openEdit(row);
  if (action === "delete") removeModel(row);
}

async function removeModel(row) {
  await deleteLogicalModel(row.id);
  ElMessage.success("已删除统一模型");
  await resetPage();
}

onMounted(() => {
  loadModelTypes();
  nextTick(() => {
    setupAutoLayout();
    recalcLayout(true);
  });
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  if (resizeTimer) {
    clearTimeout(resizeTimer);
  }
});

function setupAutoLayout() {
  if (typeof ResizeObserver === "undefined") {
    loadOnce();
    return;
  }
  resizeObserver = new ResizeObserver(() => {
    if (resizeTimer) {
      clearTimeout(resizeTimer);
    }
    resizeTimer = setTimeout(() => recalcLayout(false), 120);
  });
  [getBodyEl(), getToolbarEl(), getPaginationEl()]
    .filter(Boolean)
    .forEach((el) => resizeObserver.observe(el));
}

function recalcLayout(forceLoad) {
  const body = getBodyEl();
  if (!body) {
    loadOnce();
    return;
  }

  const width = body.clientWidth;
  const height = getAvailableGridHeight();
  if (width <= 0 || height <= 0) {
    loadOnce();
    return;
  }

  const columns = clamp(Math.floor((width + GAP_X) / (MIN_CARD_WIDTH + GAP_X)), 1, MAX_COLUMNS);
  const rowCount = clamp(Math.floor((height + GAP_Y) / (MIN_CARD_HEIGHT + GAP_Y)), 1, MAX_ROWS);
  const cardHeight = Math.floor((height - GAP_Y * (rowCount - 1)) / rowCount);
  const nextPageSize = columns * rowCount;
  const changed = nextPageSize !== pageSize.value;

  layout.columns = columns;
  layout.rows = rowCount;
  layout.gridHeight = height;
  layout.cardHeight = cardHeight;

  if (changed) {
    pageSize.value = nextPageSize;
    pageNum.value = 1;
    loadOnce();
  } else if (forceLoad || !loaded) {
    loadOnce();
  }
}

function loadOnce() {
  loaded = true;
  return load();
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function getAvailableGridHeight() {
  const bodyHeight = getBodyEl()?.clientHeight || 0;
  const reservedHeight = getOuterHeight(getToolbarEl()) + getOuterHeight(getPaginationEl());
  return Math.max(0, Math.floor(bodyHeight - reservedHeight - LAYOUT_SAFETY_GAP));
}

function getOuterHeight(el) {
  if (!el) return 0;
  const style = window.getComputedStyle(el);
  const marginTop = Number.parseFloat(style.marginTop) || 0;
  const marginBottom = Number.parseFloat(style.marginBottom) || 0;
  return el.offsetHeight + marginTop + marginBottom;
}

function getBodyEl() {
  return marketPanelRef.value?.getBodyEl?.();
}

function getToolbarEl() {
  return marketPanelRef.value?.getToolbarEl?.();
}

function getPaginationEl() {
  return marketPanelRef.value?.getPaginationEl?.();
}
</script>

<style scoped>
.market-page {
  height: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.market-page :deep(.slot-body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
