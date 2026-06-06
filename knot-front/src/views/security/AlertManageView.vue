<template>
  <PageSection title="告警管理">
    <ListPageHeader>
      <template #actions>
        <el-button @click="load">刷新</el-button>
      </template>
      <template #filters>
        <div class="list-filter-item list-filter-item--grow">
          <span class="list-filter-label">关键词</span>
          <el-input
            v-model="keyword"
            class="list-filter-control--wide"
            placeholder="按告警 ID、级别、标题、状态筛选"
            clearable
          />
        </div>
      </template>
    </ListPageHeader>

    <AlertListPanel
      :rows="filteredRows"
      :loading="loading"
      :total="filteredRows.length"
      :page-num="1"
      :page-size="filteredRows.length || pageSize"
    />
  </PageSection>
</template>

<script setup>
import { computed, ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import ListPageHeader from "../../components/common/ListPageHeader.vue";
import AlertListPanel from "../../components/security/AlertListPanel.vue";
import { usePageList } from "../../composables/usePageList";
import { listSecurityAlerts } from "../../api/security";

const keyword = ref("");
const { rows, loading, pageSize, load } = usePageList(listSecurityAlerts);

const filteredRows = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  if (!value) return rows.value;
  return rows.value.filter((row) =>
    [row.alertId, row.level, row.title, row.status].some((item) =>
      String(item || "").toLowerCase().includes(value)
    )
  );
});

load();
</script>
