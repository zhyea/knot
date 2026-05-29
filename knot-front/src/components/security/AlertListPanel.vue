<template>
  <div>
    <div class="toolbar">
      <el-button @click="emit('refresh')">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" size="small" stripe border>
      <el-table-column prop="alertId" label="告警 ID" width="120" />
      <el-table-column prop="level" label="级别" width="80" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column prop="status" label="状态" width="90" />
    </el-table>
    <div class="pagination-wrap">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        :page-sizes="[10, 20, 50]"
        @current-change="(page) => emit('page-change', page)"
        @size-change="(size) => emit('size-change', size)"
      />
    </div>
  </div>
</template>

<script setup>
defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 }
});

const emit = defineEmits(["refresh", "page-change", "size-change"]);
</script>
