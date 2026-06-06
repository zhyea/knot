<template>
  <div ref="rootRef" class="list-pagination">
    <el-button v-if="showRefresh" @click="emit('refresh')">刷新</el-button>
    <el-pagination
      background
      :layout="layout"
      :total="total"
      :page-size="pageSize"
      :current-page="pageNum"
      :page-sizes="pageSizes"
      @current-change="(page) => emit('page-change', page)"
      @size-change="(size) => emit('size-change', size)"
    />
  </div>
</template>

<script setup>
import { ref } from "vue";

defineProps({
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  pageSizes: { type: Array, default: () => [10, 20, 50] },
  layout: { type: String, default: "total, sizes, prev, pager, next" },
  showRefresh: { type: Boolean, default: true }
});

const emit = defineEmits(["refresh", "page-change", "size-change"]);
const rootRef = ref(null);

defineExpose({
  getRootEl: () => rootRef.value
});
</script>

<style scoped>
.list-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .list-pagination {
    flex-direction: column;
    align-items: stretch;
  }

  .list-pagination :deep(.el-pagination) {
    justify-content: flex-start;
  }
}
</style>
