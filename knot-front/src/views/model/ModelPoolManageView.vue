<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按模型池编码、名称筛选"
          @query="handleQuery"
        />
        <FilterField label="模型类型" :width="260">
          <EnumControl
            v-model="query.modelTypes"
            enum-name="ModelTypeEnum"
            multiple
            collapse-tags
            collapse-tags-tooltip
            clearable
          />
        </FilterField>
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建模型池</el-button>
          </div>
        </div>

        <ModelPoolListPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :show-refresh="false"
          @edit="openEdit"
          @delete="remove"
          @page-change="onPageChange"
          @size-change="onSizeChange"
          @changed="load"
        />
      </section>
    </div>

    <ModelPoolFormDrawer v-model="formVisible" :pool="editingPool" @saved="resetPage" />
  </PageSection>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import { useListQuery } from "../../composables/useListQuery";
import EnumControl from "../../components/common/EnumControl.vue";
import ModelPoolListPanel from "../../components/model/ModelPoolListPanel.vue";
import ModelPoolFormDrawer from "../../components/model/ModelPoolFormDrawer.vue";
import { deleteModelPool, listModelPools } from "../../api/modelPools";

const {
  query,
  rows,
  loading,
  total,
  pageNum,
  pageSize,
  load,
  onPageChange,
  onSizeChange,
  resetPage,
  handleQuery,
  handleReset
} = useListQuery({ apiFn: listModelPools, fields: { keyword: "", modelTypes: [] } });

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
  await ElMessageBox.confirm(`确认删除模型池“${row.name || row.poolCode}”？`, "删除确认", {
    type: "warning"
  });
  await deleteModelPool(row.id);
  ElMessage.success("已删除");
  resetPage();
}


onMounted(load);
</script>
