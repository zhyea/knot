<template>
  <PageSection title="通知模板">
    <NotifyTemplateListPanel
      :rows="rows"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      @create="tplDlg = true"
      @refresh="load"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    />

    <NotifyTemplateFormDialog v-model="tplDlg" @saved="resetPage" />
  </PageSection>
</template>

<script setup>
import { ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import NotifyTemplateFormDialog from "../../components/notifications/NotifyTemplateFormDialog.vue";
import NotifyTemplateListPanel from "../../components/notifications/NotifyTemplateListPanel.vue";
import { usePageList } from "../../composables/usePageList";
import { listNotifyTemplates } from "../../api/notifications";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listNotifyTemplates);
const tplDlg = ref(false);

load();
</script>
