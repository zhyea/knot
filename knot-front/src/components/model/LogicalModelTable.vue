<template>
  <div>
    <el-table
      v-loading="loading"
      :data="rows"
      stripe
      border
      style="width: 100%"
    >
      <el-table-column label="模型名称" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.displayName || row.modelName || "-" }}</template>
      </el-table-column>
      <el-table-column prop="modelCode" label="模型编码" min-width="160" show-overflow-tooltip />
      <el-table-column label="类型" width="110" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tag v-if="modelTypeLabel(row.modelType) !== '-'" size="small" effect="plain">
            {{ modelTypeLabel(row.modelType) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="modelFamily" label="模型族" min-width="120" show-overflow-tooltip />
      <el-table-column label="标签" min-width="180">
        <template #default="{ row }">
          <div v-if="displayTags(row).length" class="tag-list">
            <el-tag v-for="tag in displayTags(row)" :key="tag" size="small" effect="plain">
              {{ tag }}
            </el-tag>
          </div>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row)" size="small" disable-transitions>
            {{ statusText(row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'delete', label: '删除', icon: Delete, type: 'danger', confirm: '确认删除该统一模型？' }
            ]"
            @action="(action) => emit('action', action, row)"
          />
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无统一模型" />
      </template>
    </el-table>

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
  pageSize: { type: Number, default: 20 },
  pageSizes: { type: Array, default: () => [10, 20, 50] },
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
  return tags.slice(0, 3);
}

function statusText(row) {
  if (row.featured) return "推荐";
  if (row.publishStatus === "PUBLISHED") return "已发布";
  return row.enabled ? "可用" : "草稿";
}

function statusTagType(row) {
  if (row.featured) return "warning";
  if (row.publishStatus === "PUBLISHED") return "success";
  return row.enabled ? "success" : "info";
}

function formatDateTime(value) {
  if (!value) return "-";
  return String(value).replace("T", " ").slice(0, 19);
}
</script>

<style scoped>
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
