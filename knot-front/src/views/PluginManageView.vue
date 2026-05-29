<template>
  <PageSection title="插件管理">
    <PluginListPanel
      :rows="pluginRows"
      :status-options="pluginStatusOptions"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      @create="dlg = true"
      @refresh="pageLoad"
      @status-change="onStatus"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    />

    <PluginFormDialog v-model="dlg" @saved="resetPage" />
  </PageSection>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import PluginFormDialog from "../components/plugin/PluginFormDialog.vue";
import PluginListPanel from "../components/plugin/PluginListPanel.vue";
import { useEnums } from "../composables/useEnums";
import { usePageList } from "../composables/usePageList";
import { listPlugins, updatePluginStatus } from "../api/plugins";

const { options: statusOptions, loadOptions: loadStatusOptions } = useEnums("status");
const pluginStatusOptions = computed(() =>
  statusOptions.value.filter((item) => ["ENABLED", "DISABLED"].includes(item.itemCode))
);

onMounted(() => loadStatusOptions());

const { rows, loading, total, pageNum, pageSize, load: pageLoad, onPageChange, onSizeChange, resetPage } =
  usePageList(listPlugins);
const pluginRows = ref([]);
const dlg = ref(false);

watch(
  rows,
  (list) => {
    pluginRows.value = list.map((row) => ({ ...row, _st: null }));
  },
  { immediate: true }
);

async function onStatus(row, status) {
  if (!status) return;
  try {
    await updatePluginStatus(row.id, { status });
    ElMessage.success("已更新");
    row._st = null;
    await resetPage();
  } catch {
    row._st = null;
  }
}

pageLoad();
</script>
