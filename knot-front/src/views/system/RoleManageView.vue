<template>
  <PageSection title="角色权限">
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
            placeholder="按编码、名称筛选"
            clearable
          />
        </div>
      </template>
    </ListPageHeader>

    <RoleListPanel
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
