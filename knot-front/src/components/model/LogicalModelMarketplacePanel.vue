<template>
  <div ref="bodyRef" class="market-body">
    <div ref="toolbarRef" class="market-toolbar">
      <div>
        <div class="page-subtitle">统一对外模型入口，供应商真实模型通过映射维护。</div>
      </div>
      <div class="toolbar-actions">
        <el-button @click="emit('refresh')">刷新</el-button>
        <el-button type="primary" @click="emit('create')">新建统一模型</el-button>
      </div>
    </div>

    <div v-loading="loading" class="model-grid" :style="gridStyle">
      <div v-for="row in rows" :key="row.id" class="model-card">
        <div class="corner-ribbon" :class="ribbonClass(row)">{{ ribbonText(row) }}</div>
        <div class="card-top">
          <div class="model-main">
            <div class="model-name">{{ row.displayName || row.modelName }}</div>
            <div class="tag-list">
              <el-tag v-for="tag in displayTags(row)" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
            </div>
          </div>
        </div>
        <p class="tagline">{{ row.tagline || row.description || "暂无介绍" }}</p>
        <div class="card-footer">
          <div class="footer-meta">
            <strong>{{ row.ownerTeam || row.modelFamily || "—" }}</strong>
            <span>更新时间：{{ formatDate(row.updatedAt) }}</span>
          </div>
          <RowActions
            :actions="[
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'delete', label: '删除', icon: Delete, type: 'danger', confirm: '确认删除该统一模型？' }
            ]"
            @action="(action) => emit('action', action, row)"
          />
        </div>
      </div>
      <el-empty v-if="!loading && !rows.length" description="暂无统一模型" />
    </div>

    <div ref="paginationRef" class="pagination-wrap">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        @current-change="(page) => emit('page-change', page)"
      />
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { Delete, Edit } from "@element-plus/icons-vue";
import RowActions from "../common/RowActions.vue";

const props = defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 9 },
  gridStyle: { type: Object, default: () => ({}) },
  modelTypeOptions: { type: Array, default: () => [] }
});

const emit = defineEmits(["refresh", "create", "action", "page-change"]);

const bodyRef = ref(null);
const toolbarRef = ref(null);
const paginationRef = ref(null);

function modelTypeLabel(code) {
  if (!code) return "—";
  const item = props.modelTypeOptions.find((i) => i.itemCode === code);
  return item?.itemLabel || code;
}

function displayTags(row) {
  const tags = Array.isArray(row.tags) ? row.tags.filter(isMeaningfulTag) : [];
  const type = modelTypeLabel(row.modelType);
  if (type && type !== "—") {
    tags.unshift(type);
  }
  return tags.slice(0, 3);
}

function isMeaningfulTag(tag) {
  return typeof tag === "string" && tag.trim() && !/^\d+$/.test(tag.trim());
}

function ribbonText(row) {
  if (row.featured) return "推荐";
  if (row.publishStatus === "PUBLISHED") return "已发布";
  return row.enabled ? "可用" : "草稿";
}

function ribbonClass(row) {
  if (row.featured) return "corner-ribbon--hot";
  if (row.publishStatus === "PUBLISHED") return "corner-ribbon--new";
  return row.enabled ? "corner-ribbon--new" : "corner-ribbon--draft";
}

function formatDate(value) {
  if (!value) return "—";
  return String(value).slice(0, 10);
}

defineExpose({
  getBodyEl: () => bodyRef.value,
  getToolbarEl: () => toolbarRef.value,
  getPaginationEl: () => paginationRef.value
});
</script>

<style scoped>
.market-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.market-toolbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-subtitle {
  color: #606266;
  font-size: 13px;
}

.toolbar-actions {
  display: inline-flex;
  gap: 10px;
}

.model-grid {
  flex: 0 0 var(--market-grid-height);
  height: var(--market-grid-height);
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(var(--market-columns), minmax(0, 1fr));
  grid-auto-rows: var(--market-card-height);
  gap: 18px 22px;
  overflow: hidden;
}

.model-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 18px 16px;
  overflow: hidden;
  border: 1px solid var(--knot-border, #e4e7ed);
  background: var(--knot-surface, #fff);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
  min-height: 0;
  height: 100%;
}

.card-top {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding-right: 72px;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: auto;
  padding-top: 18px;
  border-top: 1px solid var(--knot-border, #ebeef5);
}

.model-main {
  min-width: 0;
}

.model-name {
  font-size: 16px;
  line-height: 1.4;
  font-weight: 600;
  color: var(--knot-text, #303133);
}

.tagline {
  margin: 0;
  min-height: 44px;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  margin-top: 12px;
  gap: 6px;
}

.tag-list :deep(.el-tag) {
  height: 22px;
  padding: 0 8px;
  border-color: #a9c0ff;
  background: #f7faff;
  color: #3d70ff;
  font-size: 12px;
  line-height: 20px;
}

.footer-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: var(--knot-text, #303133);
  font-size: 13px;
}

.footer-meta strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.footer-meta span {
  flex: 0 0 auto;
}

.pagination-wrap {
  flex: 0 0 auto;
}

.corner-ribbon {
  position: absolute;
  top: 0;
  right: 0;
  min-width: 78px;
  height: 30px;
  padding: 6px 8px 0 24px;
  clip-path: polygon(16px 0, 100% 0, 100% 100%, 0 0);
  text-align: right;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
}

.corner-ribbon--hot {
  background: #fff4e6;
  color: #ff9d24;
}

.corner-ribbon--new {
  background: #eafff7;
  color: #48a783;
}

.corner-ribbon--draft {
  background: #f4f4f5;
  color: #909399;
}

@media (max-width: 720px) {
  .market-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-actions {
    justify-content: flex-end;
  }

  .card-top {
    padding-right: 56px;
  }

  .footer-meta {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
    font-size: 15px;
  }
}
</style>
