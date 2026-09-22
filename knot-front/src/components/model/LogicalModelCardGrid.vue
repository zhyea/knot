<template>
  <div>
    <div v-loading="loading" class="market-body">
      <div v-if="rows.length" class="model-grid">
        <div v-for="row in rows" :key="row.id" class="model-card">
          <div class="corner-ribbon" :class="ribbonClass(row)">{{ statusText(row) }}</div>
          <div class="card-top">
            <div class="model-main">
              <div class="model-name">{{ row.displayName || row.modelName || "-" }}</div>
              <div class="model-code">{{ row.modelCode || "-" }}</div>
            </div>
          </div>
          <div v-if="displayTags(row).length" class="tag-list">
            <el-tag v-for="tag in displayTags(row)" :key="tag" size="small" effect="plain">
              {{ tag }}
            </el-tag>
          </div>
          <p class="tagline">{{ row.tagline || row.description || "暂无介绍" }}</p>
          <div class="card-footer">
            <div class="footer-meta">
              <strong>{{ row.modelFamily || "-" }}</strong>
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
      </div>
      <el-empty v-else-if="!loading" description="暂无统一模型" />
    </div>

    <ListPagination
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      :page-sizes="pageSizes"
      :show-refresh="showRefresh"
      @refresh="emit('refresh')"
      @page-change="(page) => emit('page-change', page)"
      @size-change="(size) => emit('size-change', size)"
    />
  </div>
</template>

<script setup>
import { Delete, Edit } from "@element-plus/icons-vue";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";

const props = defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 12 },
  pageSizes: { type: Array, default: () => [12, 24, 48] },
  modelTypeOptions: { type: Array, default: () => [] },
  showRefresh: { type: Boolean, default: true }
});

const emit = defineEmits(["action", "refresh", "page-change", "size-change"]);

function modelTypeLabel(code) {
  if (!code) return "-";
  const item = props.modelTypeOptions.find((option) => option.itemCode === code);
  return item?.itemLabel || code;
}

function isMeaningfulTag(tag) {
  return typeof tag === "string" && tag.trim() && !/^\d+$/.test(tag.trim());
}

function displayTags(row) {
  const tags = Array.isArray(row.tags) ? row.tags.filter(isMeaningfulTag) : [];
  const type = modelTypeLabel(row.modelType);
  if (type && type !== "-") {
    tags.unshift(type);
  }
  return tags.slice(0, 3);
}

function statusText(row) {
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
  if (!value) return "-";
  return String(value).slice(0, 10);
}
</script>

<style scoped>
.market-body {
  min-height: 220px;
}

.model-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 18px 22px;
}

.model-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  overflow: hidden;
  border: 1px solid var(--knot-border, #e4e7ed);
  background: var(--knot-surface, #fff);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.card-top {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding-right: 72px;
}

.model-main {
  min-width: 0;
}

.model-name {
  font-size: 16px;
  line-height: 1.4;
  font-weight: 600;
  color: var(--knot-text, #303133);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-code {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tagline {
  margin: 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
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

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;
  min-height: 36px;
  padding-top: 14px;
  border-top: 1px solid var(--knot-border, #ebeef5);
}

.footer-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--knot-text, #303133);
  font-size: 12px;
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
</style>
