<template>
  <PageSection title="网关节点">
    <div class="toolbar">
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border size="small">
      <el-table-column prop="nodeId" label="节点" />
      <el-table-column prop="host" label="主机" />
      <el-table-column prop="status" label="状态" width="100" />
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
import { listNodes } from "../../api/system";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange } = usePageList(listNodes);

load();
</script>

