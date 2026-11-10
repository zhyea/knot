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

import {onMounted} from "vue";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import {useListQuery} from "@/composables/useListQuery";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import ProviderAccountListPanel from "../../components/provider/ProviderAccountListPanel.vue";
import ProviderAccountFormDrawer from "../../components/provider/ProviderAccountFormDrawer.vue";
import {listProviderOperationLogs} from "@/api/operationLogs";
import {listProviders} from "@/api/providers";
import {useLogDrawer} from "@/composables/useLogDrawer";
import {useFormDrawer} from "@/composables/useFormDrawer";
const {id: logProviderId, name: logProviderName, visible: logDrawer, open: openChangeLog} = useLogDrawer({labelFields: ["code"]});
const {visible: formVisible, editing: editingProviderId, openCreate, openEdit} = useFormDrawer<number | null>({emptyValue: null, toEditing: (row) => row.id as number});

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


function onProviderSaved() {
  load();
}

function loadProviderOperationLogs() {
  return listProviderOperationLogs(logProviderId.value!);
}


onMounted(load);
</script>
