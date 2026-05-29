<template>
  <PageSection title="角色权限">
    <RoleListPanel
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
import RoleListPanel from "../../components/system/RoleListPanel.vue";
import { usePageList } from "../../composables/usePageList";
import { listSystemRoles } from "../../api/system";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange } = usePageList(listSystemRoles);

load();
</script>
