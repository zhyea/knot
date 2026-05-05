<template>
  <PageSection title="切换日志" description="查看近期路由切换日志。">
    <div class="toolbar">
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border size="small">
      <el-table-column prop="reason" label="原因" min-width="120" />
      <el-table-column prop="fromTarget" label="从" min-width="100" />
      <el-table-column prop="toTarget" label="到" min-width="100" />
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
import { listSwitchLogs } from "../../api/routing";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange } = usePageList(listSwitchLogs);

load();
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
