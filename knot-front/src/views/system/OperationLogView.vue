<template>
  <PageSection title="操作日志">
    <div class="toolbar">
      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="模块">
          <el-input v-model="queryForm.module" placeholder="请输入模块" clearable />
        </el-form-item>
        <el-form-item label="操作">
          <el-input v-model="queryForm.operation" placeholder="请输入操作" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择状态" clearable>
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILURE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border size="small" @row-click="onLogRow">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column prop="operation" label="操作" width="120" />
      <el-table-column prop="entityType" label="实体类型" width="120" />
      <el-table-column prop="entityName" label="实体名称" min-width="150" />
      <el-table-column prop="operatorName" label="操作人" width="100" />
      <el-table-column prop="ipAddress" label="IP地址" width="140" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
            {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="executionTime" label="执行时间(ms)" width="120" />
      <el-table-column prop="createdAt" label="操作时间" width="180" />
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
    <p class="hint">点击行查看详细信息</p>

    <el-drawer v-model="detailDrawer" title="日志详情" size="600px">
      <el-descriptions :column="2" border v-if="currentLog">
        <el-descriptions-item label="模块">{{ currentLog.module }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ currentLog.operation }}</el-descriptions-item>
        <el-descriptions-item label="实体类型">{{ currentLog.entityType }}</el-descriptions-item>
        <el-descriptions-item label="实体ID">{{ currentLog.entityId }}</el-descriptions-item>
        <el-descriptions-item label="实体名称">{{ currentLog.entityName }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ currentLog.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ currentLog.ipAddress }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentLog.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
            {{ currentLog.status === 'SUCCESS' ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="执行时间(ms)">{{ currentLog.executionTime }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ currentLog.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ currentLog.description }}</el-descriptions-item>
        <el-descriptions-item label="旧值" :span="2">
          <el-input v-if="currentLog.oldValue" v-model="currentLog.oldValue" type="textarea" :rows="4" readonly />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="新值" :span="2">
          <el-input v-if="currentLog.newValue" v-model="currentLog.newValue" type="textarea" :rows="4" readonly />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2" v-if="currentLog.errorMsg">
          <el-input v-model="currentLog.errorMsg" type="textarea" :rows="4" readonly />
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </PageSection>
</template>

<script setup>
import { ref, reactive } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import { usePageList } from "../../composables/usePageList";
import { listOperationLogs, getOperationLogDetail } from "../../api/operationLogs";

const queryForm = reactive({
  module: '',
  operation: '',
  status: ''
});

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange } = usePageList(listOperationLogs);
const detailDrawer = ref(false);
const currentLog = ref(null);

function handleQuery() {
  load();
}

function handleReset() {
  queryForm.module = '';
  queryForm.operation = '';
  queryForm.status = '';
  load();
}

async function onLogRow(row) {
  const d = await getOperationLogDetail(row.id);
  currentLog.value = d;
  detailDrawer.value = true;
}

load();
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
.query-form {
  flex: 1;
}
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
