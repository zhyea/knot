<template>
  <PageSection title="供应商模型池">
    <ListPageHeader>
      <template #actions>
        <el-button type="primary" @click="openCreate">新建模型池</el-button>
        <el-button @click="load">刷新</el-button>
      </template>
      <template #filters>
        <div class="list-filter-item list-filter-item--grow">
          <span class="list-filter-label">关键词</span>
          <el-input
            v-model="query.keyword"
            class="list-filter-control--wide"
            placeholder="按模型池编码、名称筛选"
            clearable
            @keyup.enter="handleQuery"
          />
        </div>
        <div class="list-filter-item">
          <span class="list-filter-label">模型类型</span>
          <EnumSelect
            v-model="query.modelTypes"
            class="list-filter-control--wide"
            category="model_type"
            multiple
            collapse-tags
            collapse-tags-tooltip
            clearable
          />
        </div>
        <div class="list-filter-actions">
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </template>
    </ListPageHeader>

    <ModelPoolListPanel
      :rows="rows"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      @edit="openEdit"
      @delete="remove"
      @page-change="onPageChange"
      @size-change="onSizeChange"
      @changed="load"
    />
    <ModelPoolFormDrawer v-model="formVisible" :pool="editingPool" @saved="resetPage" />
  </PageSection>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import ListPageHeader from "../components/common/ListPageHeader.vue";
import EnumSelect from "../components/common/EnumSelect.vue";
import ModelPoolListPanel from "../components/model/ModelPoolListPanel.vue";
import ModelPoolFormDrawer from "../components/model/ModelPoolFormDrawer.vue";
import { deleteModelPool, listModelPools } from "../api/modelPools";
import { usePageList } from "../composables/usePageList";

const query = reactive({
  keyword: "",
  modelTypes: []
});

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listModelPools, { extra: query });

const formVisible = ref(false);
const editingPool = ref(null);

function openCreate() {
  editingPool.value = null;
  formVisible.value = true;
}

function openEdit(row) {
  editingPool.value = row;
  formVisible.value = true;
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除模型池「${row.name || row.poolCode}」？`, "删除确认", { type: "warning" });
  await deleteModelPool(row.id);
  ElMessage.success("已删除");
  resetPage();
}

function handleQuery() {
  return resetPage();
}

function handleReset() {
  query.keyword = "";
  query.modelTypes = [];
  return resetPage();
}

onMounted(load);
</script>
