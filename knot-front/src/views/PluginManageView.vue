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
              placeholder="按编码、名称、类型筛选"
              clearable
              @keyup.enter="handleQuery"
            />
          </div>
          <div class="list-filter-item">
            <span class="list-filter-label">状态</span>
            <el-select v-model="query.status" class="list-filter-control" clearable placeholder="全部">
              <el-option
                v-for="item in pluginStatusOptions"
                :key="item.itemCode"
                :label="item.itemLabel"
                :value="item.itemCode"
              />
            </el-select>
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
            <el-button type="primary" @click="dlg = true">新建插件</el-button>
          </div>
        </div>

        <PluginListPanel
          :rows="pluginRows"
          :status-options="pluginStatusOptions"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :show-refresh="false"
          @status-change="onStatus"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <PluginFormDialog v-model="dlg" @saved="resetPage" />
  </PageSection>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
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

const query = reactive({
  keyword: "",
  status: ""
});

const { rows, loading, total, pageNum, pageSize, load: pageLoad, onPageChange, onSizeChange, resetPage } =
  usePageList(listPlugins, { extra: query });

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

function handleQuery() {
  return resetPage();
}

function handleReset() {
  query.keyword = "";
  query.status = "";
  return resetPage();
}

onMounted(() => {
  loadStatusOptions();
  pageLoad();
});
</script>
