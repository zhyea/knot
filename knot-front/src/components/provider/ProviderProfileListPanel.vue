<template>
  <div class="list-page-table">
    <el-table v-loading="loading" :data="rows" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center" header-align="center" />
      <el-table-column prop="code" label="编码" min-width="18%" show-overflow-tooltip />
      <el-table-column prop="name" label="名称" min-width="22%" show-overflow-tooltip />
      <el-table-column label="分类" min-width="22%">
        <template #default="{ row }">
          <template v-if="splitTags(row.tag).length">
            <el-tag v-for="item in splitTags(row.tag)" :key="item" size="small" class="tag-item">
              {{ item }}
            </el-tag>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="18%">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="18%">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'log', label: '操作记录', icon: Document },
              { key: 'delete', label: '删除', icon: Delete, type: 'danger', confirm: `确认删除「${row.name}」？` }
            ]"
            @action="$emit('action', $event, row)"
          />
        </template>
      </el-table-column>
    </el-table>

    <ListPagination
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      :show-refresh="false"
      @page-change="$emit('page-change', $event)"
      @size-change="$emit('size-change', $event)"
    />
  </div>
</template>

<script setup>
import { Delete, Document, Edit } from "@element-plus/icons-vue";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";

defineProps({
  rows: { type: Array, default: () => [] },
  loading: Boolean,
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 }
});

defineEmits(["action", "page-change", "size-change"]);

function splitTags(raw) {
  if (!raw) return [];
  return String(raw).split(",").map((item) => item.trim()).filter(Boolean);
}

function formatTime(value) {
  return value ? String(value).replace("T", " ") : "-";
}
</script>

<style scoped>
.tag-item {
  margin-right: 4px;
}
</style>
