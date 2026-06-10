<template>
  <PageSection class="market-page">
    <div class="list-page-shell list-page-shell--fill market-shell">
      <section ref="headerRef" class="list-page-block">
        <div class="list-page-filters">
          <div class="list-filter-item list-filter-item--grow">
            <span class="list-filter-label">关键词</span>
            <el-input
              v-model="query.keyword"
              class="list-filter-control--wide"
              placeholder="按模型编码、名称、模型族筛选"
              clearable
              @keyup.enter="handleQuery"
            />
          </div>
          <div class="list-filter-item market-filter-item market-filter-item--type">
            <span class="list-filter-label">模型类型</span>
            <EnumSelect
              v-model="query.modelTypes"
              class="list-filter-control--wide market-filter-control--type"
              category="model_type"
              multiple
              collapse-tags
              collapse-tags-tooltip
              clearable
            />
          </div>
          <div class="list-filter-actions">
            <el-button type="primary" @click="handleQuery">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </div>
        </div>
      </section>

      <section class="list-page-block list-page-block--content list-page-block--fill">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建统一模型</el-button>
          </div>
          <div class="list-page-toolbar__meta">
            <div class="list-page-toolbar__title">
              统一对外模型入口，供应商真实模型通过映射维护。
            </div>
          </div>
        </div>

        <LogicalModelMarketplacePanel
          ref="marketPanelRef"
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :grid-style="gridStyle"
          :compact-level="compactLevel"
          :model-type-options="modelTypeOptions"
          :show-refresh="false"
          @action="handleAction"
          @page-change="onPageChange"
        />
      </section>
    </div>

    <LogicalModelFormDrawer v-model="formVisible" :model="editingModel" @saved="resetPage" />
  </PageSection>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import EnumSelect from "../components/common/EnumSelect.vue";
import LogicalModelFormDrawer from "../components/model/LogicalModelFormDrawer.vue";
import LogicalModelMarketplacePanel from "../components/model/LogicalModelMarketplacePanel.vue";
import { deleteLogicalModel, listLogicalModels } from "../api/logicalModels";
import { useAutoQuery } from "../composables/useAutoQuery";
import { useEnums } from "../composables/useEnums";
import { usePageList } from "../composables/usePageList";

const query = reactive({
  keyword: "",
  modelTypes: []
});

const { rows, loading, total, pageNum, pageSize, load, onPageChange, resetPage } =
  usePageList(listLogicalModels, { pageSize: 9, extra: query });
const { pauseAutoQuery } = useAutoQuery(query, handleQuery);

const { options: modelTypeOptions, loadOptions: loadModelTypes } = useEnums("model_type");
const headerRef = ref(null);
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
const MIN_CARD_HEIGHT = 188;
const MAX_COLUMNS = 5;
const MAX_ROWS = 5;
const LAYOUT_SAFETY_GAP = 2;
let resizeObserver;
let resizeTimer;
let loaded = false;

const gridStyle = computed(() => {
  const density = resolveDensity(layout.columns);
  return {
    "--market-columns": layout.columns,
    "--market-rows": layout.rows,
    "--market-grid-height": layout.gridHeight ? `${layout.gridHeight}px` : "auto",
    "--market-card-height": `${layout.cardHeight}px`,
    "--market-card-padding": `${density.cardPadding}px`,
    "--market-card-gap": `${density.cardGap}px`,
    "--market-title-size": `${density.titleSize}px`,
    "--market-tagline-min-height": `${density.taglineMinHeight}px`,
    "--market-tagline-font-size": `${density.taglineFontSize}px`,
    "--market-tagline-line-height": density.taglineLineHeight,
    "--market-footer-gap": `${density.footerGap}px`,
    "--market-footer-font-size": `${density.footerFontSize}px`,
    "--market-footer-padding-top": `${density.footerPaddingTop}px`,
    "--market-tag-gap": `${density.tagGap}px`,
    "--market-tag-font-size": `${density.tagFontSize}px`
  };
});

const compactLevel = computed(() => {
  if (layout.columns >= 5) return 2;
  if (layout.columns >= 4) return 1;
  return 0;
});

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

function handleQuery() {
  return pauseAutoQuery(() => {
    loaded = false;
    return resetPage();
  });
}

function handleReset() {
  return pauseAutoQuery(() => {
    query.keyword = "";
    query.modelTypes = [];
    loaded = false;
    return resetPage();
  });
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
  [getViewportEl(), getHeaderEl(), getPaginationEl()]
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

function resolveDensity(columns) {
  if (columns >= 5) {
    return {
      cardPadding: 13,
      cardGap: 10,
      titleSize: 14,
      taglineMinHeight: 34,
      taglineFontSize: 12,
      taglineLineHeight: 1.45,
      footerGap: 8,
      footerFontSize: 11,
      footerPaddingTop: 12,
      tagGap: 5,
      tagFontSize: 11
    };
  }
  if (columns >= 4) {
    return {
      cardPadding: 14,
      cardGap: 11,
      titleSize: 15,
      taglineMinHeight: 36,
      taglineFontSize: 12,
      taglineLineHeight: 1.5,
      footerGap: 10,
      footerFontSize: 12,
      footerPaddingTop: 13,
      tagGap: 6,
      tagFontSize: 11
    };
  }
  return {
    cardPadding: 16,
    cardGap: 12,
    titleSize: 16,
    taglineMinHeight: 40,
    taglineFontSize: 13,
    taglineLineHeight: 1.5,
    footerGap: 12,
    footerFontSize: 12,
    footerPaddingTop: 14,
    tagGap: 6,
    tagFontSize: 12
  };
}

function getAvailableGridHeight() {
  const viewport = getViewportEl();
  const body = getBodyEl();
  if (!viewport || !body) {
    return 0;
  }
  const viewportRect = viewport.getBoundingClientRect();
  const bodyRect = body.getBoundingClientRect();
  const bodyHeight = Math.max(0, Math.floor(viewportRect.bottom - bodyRect.top));
  const reservedHeight =
    getOuterHeight(getHeaderEl()) +
    getOuterHeight(getToolbarEl()) +
    getOuterHeight(getPaginationEl()) +
    getBodyBottomInset();
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

function getHeaderEl() {
  return headerRef.value;
}

function getToolbarEl() {
  return getBodyEl()?.closest(".list-page-block")?.querySelector(".list-page-toolbar") || null;
}

function getBodyBottomInset() {
  const body = getBodyEl();
  const slotBody = body?.closest(".slot-body") || null;
  const contentBlock = body?.closest(".list-page-block") || null;
  return getBoxBottomInset(contentBlock) + getBoxBottomInset(slotBody);
}

function getBoxBottomInset(el) {
  if (!el) return 0;
  const style = window.getComputedStyle(el);
  const paddingBottom = Number.parseFloat(style.paddingBottom) || 0;
  const borderBottom = Number.parseFloat(style.borderBottomWidth) || 0;
  return paddingBottom + borderBottom;
}

function getViewportEl() {
  return getBodyEl()?.closest(".el-scrollbar__wrap") || null;
}

function getPaginationEl() {
  return marketPanelRef.value?.getPaginationEl?.();
}
</script>

<style scoped>
.market-page {
  height: 100%;
}

.market-page :deep(.slot-body) {
  height: 100%;
  min-height: 0;
}

.market-shell {
  height: 100%;
}

.market-filter-item--type {
  min-width: 292px;
}

.market-filter-control--type {
  width: 260px;
}

@media (max-width: 768px) {
  .market-filter-item--type {
    min-width: 0;
  }

  .market-filter-control--type {
    width: 100%;
  }
}
</style>
