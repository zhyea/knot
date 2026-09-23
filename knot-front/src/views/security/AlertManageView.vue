<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="keyword"
          placeholder="按告警 ID、级别、标题、状态筛选"
          @query="handleQuery"
        />
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <AlertListPanel
          :rows="filteredRows"
          :loading="loading"
          :total="filteredRows.length"
          :page-num="1"
          :page-size="filteredRows.length || pageSize"
          :show-refresh="false"
        />
      </section>
    </div>
  </PageSection>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import AlertListPanel from "../../components/security/AlertListPanel.vue";
import { usePageList } from "../../composables/usePageList";
import { listSecurityAlerts } from "../../api/security";

const route = useRoute();
const router = useRouter();

const keyword = ref(route.query.keyword ? String(route.query.keyword) : "");
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

// 告警是全量下发后前端过滤，同样把关键字写回地址栏，刷新后不被清空
watch(keyword, (value) => {
  const next = { ...route.query };
  const normalized = value.trim();
  if (normalized) {
    next.keyword = normalized;
  } else {
    delete next.keyword;
  }
  router.replace({ query: next });
});

function handleReset() {
  keyword.value = "";
}

load();
</script>
