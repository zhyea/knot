<template>
  <PageSection title="告警管理" description="查看安全告警列表。">
    <div class="toolbar">
      <el-button @click="load">刷新</el-button>
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
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </PageSection>
</template>

<script setup>
import PageSection from "../../components/common/PageSection.vue";
import { usePageList } from "../../composables/usePageList";
import { listSecurityAlerts } from "../../api/security";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange } = usePageList(listSecurityAlerts);

load();
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
