<template>
  <PageSection title="成本统计" description="查看网关请求汇总与明细。">
    <el-row :gutter="12" class="stat-row" v-loading="sLoading">
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover"><div class="st">总请求</div><div class="sv">{{ summary?.requestCount ?? "—" }}</div></el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover"><div class="st">Token 合计</div><div class="sv">{{ summary?.tokenUsage ?? "—" }}</div></el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover"><div class="st">成本合计</div><div class="sv">{{ fmtMoney(summary?.totalCost) }}</div></el-card>
      </el-col>
    </el-row>
    <div class="toolbar">
      <el-button @click="loadSummary">刷新汇总</el-button>
      <el-button @click="loadDetails">刷新明细</el-button>
    </div>
    <el-table v-loading="dLoading" :data="details" stripe border size="small">
      <el-table-column prop="requestId" label="请求 ID" min-width="140" />
      <el-table-column prop="appId" label="应用" width="90" />
      <el-table-column prop="modelCode" label="模型" width="90" />
      <el-table-column prop="tokenUsage" label="Token" width="90" />
      <el-table-column prop="cost" label="成本" width="100">
        <template #default="{ row }">{{ fmtMoney(row.cost) }}</template>
      </el-table-column>
    </el-table>
    <div class="pagination-wrap">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="detailTotal"
        :page-size="detailPageSize"
        :current-page="detailPageNum"
        :page-sizes="[10, 20, 50]"
        @current-change="onDetailPageChange"
        @size-change="onDetailSizeChange"
      />
    </div>
  </PageSection>
</template>

<script setup>
import { ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import { fmtMoney } from "../../utils/format";
import { usePageList } from "../../composables/usePageList";
import { getBillingSummary, listBillingDetails } from "../../api/billing";

const summary = ref(null);
const sLoading = ref(false);

const {
  rows: details,
  loading: dLoading,
  total: detailTotal,
  pageNum: detailPageNum,
  pageSize: detailPageSize,
  load: loadDetails,
  onPageChange: onDetailPageChange,
  onSizeChange: onDetailSizeChange
} = usePageList(listBillingDetails);

async function loadSummary() {
  sLoading.value = true;
  try {
    summary.value = await getBillingSummary();
  } finally {
    sLoading.value = false;
  }
}

loadSummary();
loadDetails();
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.st {
  font-size: 13px;
  color: #909399;
}
.sv {
  font-size: 22px;
  font-weight: 600;
  margin-top: 6px;
  color: #303133;
}
</style>
