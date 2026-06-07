<template>
  <PageSection>
    <div class="list-page-shell">
      <section class="list-page-block">
        <div class="list-page-filters">
          <div class="list-filter-item list-filter-item--grow">
            <span class="list-filter-label">关键词</span>
            <el-input
              v-model="keyword"
              class="list-filter-control--wide"
              placeholder="按编码、名称筛选"
              clearable
            />
          </div>
        </div>
      </section>

      <section class="list-page-block list-page-block--content">
        <RoleListPanel
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
import { computed, ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import RoleListPanel from "../../components/system/RoleListPanel.vue";
import { usePageList } from "../../composables/usePageList";
import { listSystemRoles } from "../../api/system";

const keyword = ref("");
const { rows, loading, pageSize, load } = usePageList(listSystemRoles);

const filteredRows = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  if (!value) return rows.value;
  return rows.value.filter((row) =>
    [row.code, row.name].some((item) => String(item || "").toLowerCase().includes(value))
  );
});

load();
</script>
