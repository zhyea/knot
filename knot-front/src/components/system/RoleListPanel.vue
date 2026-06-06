<template>
  <div>
    <el-table v-loading="loading" :data="rows" size="small" stripe border>
      <el-table-column prop="code" label="编码" width="140" />
      <el-table-column prop="name" label="名称" />
    </el-table>
    <ListPagination
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      @refresh="emit('refresh')"
      @page-change="(page) => emit('page-change', page)"
      @size-change="(size) => emit('size-change', size)"
    />
  </div>
</template>

<script setup>
import ListPagination from "../common/ListPagination.vue";

defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 }
});

const emit = defineEmits(["refresh", "page-change", "size-change"]);
</script>
