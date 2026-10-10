<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按编码、名称、供应商筛选"
          @query="handleQuery"
        />
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建供应商账户</el-button>
          </div>
        </div>

        <ProviderAccountListPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :show-refresh="false"
          @edit="openEdit"
          @log="openChangeLog"
          @page-change="onPageChange"
          @size-change="onSizeChange"
          @changed="load"
        />
      </section>
    </div>

    <ProviderAccountFormDrawer
      v-model="formVisible"
      :provider-id="editingProviderId"
      @saved="onProviderSaved"
    />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`供应商变更日志 - ${logProviderName || ''}`"
      :load-logs="loadProviderOperationLogs"
    />
  </PageSection>
</template>

<script setup lang="ts">
import type {Row} from "@/types";
import {onMounted, ref} from "vue";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import {useListQuery} from "@/composables/useListQuery";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import ProviderAccountListPanel from "../../components/provider/ProviderAccountListPanel.vue";
import ProviderAccountFormDrawer from "../../components/provider/ProviderAccountFormDrawer.vue";
import {listProviderOperationLogs} from "@/api/operationLogs";
import {listProviders} from "@/api/providers";

const {
  query,
  rows,
  loading,
  total,
  pageNum,
  pageSize,
  load,
  onPageChange,
  onSizeChange,
  handleQuery,
  handleReset
} = useListQuery({ apiFn: listProviders, fields: { keyword: "" } });

const formVisible = ref(false);
const editingProviderId = ref<number | null>(null);
const logDrawer = ref(false);
const logProviderId = ref<number | null>(null);
const logProviderName = ref("");

function openCreate() {
  editingProviderId.value = null;
  formVisible.value = true;
}

function openEdit(row: Row) {
  editingProviderId.value = row.id;
  formVisible.value = true;
}

function onProviderSaved() {
  load();
}

function openChangeLog(row: Row) {
  logProviderId.value = row.id;
  logProviderName.value = row.code || `#${row.id}`;
  logDrawer.value = true;
}

function loadProviderOperationLogs() {
  return listProviderOperationLogs(logProviderId.value!);
}


onMounted(load);
</script>
