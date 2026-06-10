<template>
  <div>
    <el-table v-loading="loading" :data="rows" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" min-width="5%" align="center" header-align="center" />
      <el-table-column prop="code" label="编码" min-width="12%" />
      <el-table-column prop="name" label="名称" min-width="15%" />
      <el-table-column prop="pluginType" label="类型" min-width="10%" />
      <el-table-column prop="version" label="版本" min-width="8%" />
      <el-table-column label="状态" min-width="10%" align="center" header-align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 'ENABLED'"
            :loading="statusUpdatingId === row.id"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
            @change="(value) => emit('status-change', row, value)"
          />
        </template>
      </el-table-column>
    </el-table>

    <ListPagination
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      :show-refresh="showRefresh"
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
  statusUpdatingId: { type: Number, default: null },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  showRefresh: { type: Boolean, default: true }
});

const emit = defineEmits(["create", "refresh", "status-change", "page-change", "size-change"]);
</script>
