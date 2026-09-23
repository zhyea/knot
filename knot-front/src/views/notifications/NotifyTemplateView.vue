<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按编码、名称、渠道、内容筛选"
          @query="handleQuery"
        />
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="tplDlg = true">新建模板</el-button>
          </div>
        </div>

        <NotifyTemplateListPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :show-refresh="false"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <NotifyTemplateFormDialog v-model="tplDlg" @saved="resetPage" />
  </PageSection>
</template>

<script setup>
import { ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import { useListQuery } from "../../composables/useListQuery";
import NotifyTemplateFormDialog from "../../components/notifications/NotifyTemplateFormDialog.vue";
import NotifyTemplateListPanel from "../../components/notifications/NotifyTemplateListPanel.vue";
import { listNotifyTemplates } from "../../api/notifications";

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
  resetPage,
  handleQuery,
  handleReset
} = useListQuery({ apiFn: listNotifyTemplates, fields: { keyword: "" } });

const tplDlg = ref(false);


load();
</script>
