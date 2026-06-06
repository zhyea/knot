<template>
  <PageSection>
    <ListPageHeader>
      <template #actions>
        <el-button type="primary" @click="openCreate">新建供应商</el-button>
        <el-button @click="load">刷新</el-button>
      </template>
      <template #filters>
        <div class="list-filter-item list-filter-item--grow">
          <span class="list-filter-label">关键字</span>
          <el-input
            v-model="query.keyword"
            class="list-filter-control--wide"
            placeholder="按编码、名称、类型筛选"
            clearable
            @keyup.enter="handleQuery"
          />
        </div>
        <div class="list-filter-actions">
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </template>
    </ListPageHeader>

    <ProviderListPanel
      :rows="rows"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      @edit="openEdit"
      @discount="openDiscount"
      @log="openChangeLog"
      @page-change="onPageChange"
      @size-change="onSizeChange"
      @changed="load"
    />

    <ProviderFormDrawer
      v-model="formVisible"
      :provider="editingProvider"
      @saved="onProviderSaved"
    />

    <ProviderDiscountDrawer
      v-model="discountDrawerVisible"
      :provider-id="discountProviderId"
      @changed="load"
    />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`供应商变更日志 - ${logProviderName || ''}`"
      :load-logs="loadProviderOperationLogs"
    />
  </PageSection>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import PageSection from "../components/common/PageSection.vue";
import ListPageHeader from "../components/common/ListPageHeader.vue";
import OperationLogDrawer from "../components/common/OperationLogDrawer.vue";
import ProviderListPanel from "../components/provider/ProviderListPanel.vue";
import ProviderFormDrawer from "../components/provider/ProviderFormDrawer.vue";
import ProviderDiscountDrawer from "../components/provider/ProviderDiscountDrawer.vue";
import { listProviderOperationLogs } from "../api/operationLogs";
import { usePageList } from "../composables/usePageList";
import { listProviders } from "../api/providers";

const query = reactive({
  keyword: ""
});

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listProviders, { extra: query });

const formVisible = ref(false);
const editingProvider = ref(null);
const discountDrawerVisible = ref(false);
const discountProviderId = ref(null);
const logDrawer = ref(false);
const logProviderId = ref(null);
const logProviderName = ref("");

function openCreate() {
  editingProvider.value = null;
  formVisible.value = true;
}

function openEdit(row) {
  editingProvider.value = row;
  formVisible.value = true;
}

function onProviderSaved() {
  load();
}

function openDiscount(row) {
  discountProviderId.value = row.id;
  discountDrawerVisible.value = true;
}

function openChangeLog(row) {
  logProviderId.value = row.id;
  logProviderName.value = row.name || `#${row.id}`;
  logDrawer.value = true;
}

function loadProviderOperationLogs() {
  return listProviderOperationLogs(logProviderId.value);
}

function handleQuery() {
  return resetPage();
}

function handleReset() {
  query.keyword = "";
  return resetPage();
}

onMounted(load);
</script>
