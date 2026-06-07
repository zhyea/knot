<template>
  <div>
    <el-table v-loading="loading" :data="rows" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" min-width="5%" align="center" header-align="center" />
      <el-table-column prop="code" label="编码" min-width="12%" />
      <el-table-column prop="name" label="名称" min-width="15%" />
      <el-table-column prop="pluginType" label="类型" min-width="10%" />
      <el-table-column prop="version" label="版本" min-width="8%" />
      <el-table-column prop="status" label="状态" min-width="10%" />
      <el-table-column label="操作" width="150" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <el-select
            v-model="row._st"
            placeholder="切换状态"
            size="small"
            clearable
            style="width: 120px"
            @change="(value) => emit('status-change', row, value)"
          >
            <el-option
              v-for="item in statusOptions"
              :key="item.itemCode"
              :label="item.itemLabel"
              :value="item.itemCode"
            />
          </el-select>
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
  statusOptions: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  showRefresh: { type: Boolean, default: true }
});

const emit = defineEmits(["create", "refresh", "status-change", "page-change", "size-change"]);
</script>
