<template>
  <PageSection title="角色权限">
    <el-button class="mb" @click="load">刷新</el-button>
    <el-table v-loading="loading" :data="rows" size="small" stripe border>
      <el-table-column prop="code" label="编码" width="140" />
      <el-table-column prop="name" label="名称" />
    </el-table>
    <div class="pagination-wrap">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        :page-sizes="[10, 20, 50]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </PageSection>
</template>

<script setup>
import PageSection from "../../components/common/PageSection.vue";
import { usePageList } from "../../composables/usePageList";
import { listSystemRoles } from "../../api/system";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange } = usePageList(listSystemRoles);

load();
</script>

<style scoped>
.mb {
  margin-bottom: 10px;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
