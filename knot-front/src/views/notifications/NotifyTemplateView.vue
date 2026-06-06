<template>
  <PageSection title="通知模板">
    <ListPageHeader>
      <template #actions>
        <el-button type="primary" @click="tplDlg = true">新建模板</el-button>
        <el-button @click="load">刷新</el-button>
      </template>
      <template #filters>
        <div class="list-filter-item list-filter-item--grow">
          <span class="list-filter-label">关键词</span>
          <el-input
            v-model="query.keyword"
            class="list-filter-control--wide"
            placeholder="按编码、名称、渠道、内容筛选"
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

    <NotifyTemplateListPanel
      :rows="rows"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    />

    <NotifyTemplateFormDialog v-model="tplDlg" @saved="resetPage" />
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import ListPageHeader from "../../components/common/ListPageHeader.vue";
import NotifyTemplateFormDialog from "../../components/notifications/NotifyTemplateFormDialog.vue";
import NotifyTemplateListPanel from "../../components/notifications/NotifyTemplateListPanel.vue";
import { usePageList } from "../../composables/usePageList";
import { listNotifyTemplates } from "../../api/notifications";

const query = reactive({
  keyword: ""
});

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listNotifyTemplates, { extra: query });

const tplDlg = ref(false);

function handleQuery() {
  return resetPage();
}

function handleReset() {
  query.keyword = "";
  return resetPage();
}

load();
</script>
