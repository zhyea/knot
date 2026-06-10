<template>
  <PageSection>
    <div class="list-page-shell">
      <section class="list-page-block">
        <div class="list-page-filters">
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
        </div>
      </section>

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
import { reactive, ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import NotifyTemplateFormDialog from "../../components/notifications/NotifyTemplateFormDialog.vue";
import NotifyTemplateListPanel from "../../components/notifications/NotifyTemplateListPanel.vue";
import { useAutoQuery } from "../../composables/useAutoQuery";
import { usePageList } from "../../composables/usePageList";
import { listNotifyTemplates } from "../../api/notifications";

const query = reactive({
  keyword: ""
});

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listNotifyTemplates, { extra: query });
const { pauseAutoQuery } = useAutoQuery(query, handleQuery);

const tplDlg = ref(false);

function handleQuery() {
  return pauseAutoQuery(() => resetPage());
}

function handleReset() {
  return pauseAutoQuery(() => {
    query.keyword = "";
    return resetPage();
  });
}

load();
</script>
