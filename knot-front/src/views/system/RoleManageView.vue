<template>
  <PageSection title="角色权限">
    <div class="toolbar">
      <el-button @click="load">刷新</el-button>
    </div>
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

