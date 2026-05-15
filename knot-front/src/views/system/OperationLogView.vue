<template>
  <PageSection title="操作日志">
    <div class="toolbar">
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border size="small" @row-click="onLogRow">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="moduleCode" label="模块" width="100" />
      <el-table-column prop="actionCode" label="动作" width="100" />
      <el-table-column prop="targetId" label="目标" min-width="100" />
      <el-table-column prop="resultStatus" label="结果" width="90" />
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
    <p class="hint">点击行查看变更 JSON 详情</p>

    <el-drawer v-model="detailDrawer" title="日志详情" size="480px">
      <el-input v-model="detailText" type="textarea" :rows="18" readonly />
    </el-drawer>
  </PageSection>
</template>

<script setup>
import { ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import { usePageList } from "../../composables/usePageList";
import { listOperationLogs, getOperationLogDetail } from "../../api/system";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange } = usePageList(listOperationLogs);
const detailDrawer = ref(false);
const detailText = ref("");

async function onLogRow(row) {
  const d = await getOperationLogDetail(row.id);
  detailText.value = JSON.stringify(d, null, 2);
  detailDrawer.value = true;
}

load();
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.hint {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
</style>
