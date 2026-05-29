<template>
  <PageSection title="告警管理">
    <AlertListPanel
      :rows="rows"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      @refresh="load"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    />
  </PageSection>
</template>

<script setup>
import PageSection from "../../components/common/PageSection.vue";
import AlertListPanel from "../../components/security/AlertListPanel.vue";
import { usePageList } from "../../composables/usePageList";
import { listSecurityAlerts } from "../../api/security";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange } = usePageList(listSecurityAlerts);

load();
</script>
