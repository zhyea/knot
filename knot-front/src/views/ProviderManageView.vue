<template>
  <PageSection title="供应商管理">
    <ProviderListPanel
      :rows="rows"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      @create="openCreate"
      @refresh="load"
      @edit="openEdit"
      @discount="openDiscount"
      @log="openChangeLog"
      @page-change="onPageChange"
      @size-change="onSizeChange"
      @changed="load"
    />

    <ProviderFormDialog v-model="formVisible" :provider="editingProvider" @saved="onProviderSaved" />

    <ProviderDiscountDrawer
      v-model="discountDrawerVisible"
      :provider-id="discountProviderId"
      @changed="load"
    />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`供应商变更日志 — ${logProviderName || ''}`"
      :load-logs="loadProviderOperationLogs"
    />
  </PageSection>
</template>

<script setup>
import { ref, onMounted } from "vue";
import PageSection from "../components/common/PageSection.vue";
import OperationLogDrawer from "../components/common/OperationLogDrawer.vue";
import ProviderListPanel from "../components/provider/ProviderListPanel.vue";
import ProviderFormDialog from "../components/provider/ProviderFormDialog.vue";
import ProviderDiscountDrawer from "../components/provider/ProviderDiscountDrawer.vue";
import { listProviderOperationLogs } from "../api/operationLogs";
import { usePageList } from "../composables/usePageList";
import { listProviders } from "../api/providers";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange } = usePageList(listProviders);

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

onMounted(() => {
  load();
});
</script>
