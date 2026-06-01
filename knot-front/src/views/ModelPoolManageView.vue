<template>
  <PageSection title="供应商模型池">
    <ModelPoolListPanel
      :rows="rows"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      @create="openCreate"
      @refresh="load"
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
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import ModelPoolListPanel from "../components/model/ModelPoolListPanel.vue";
import ModelPoolFormDrawer from "../components/model/ModelPoolFormDrawer.vue";
import { deleteModelPool, listModelPools } from "../api/modelPools";
import { usePageList } from "../composables/usePageList";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } = usePageList(listModelPools);

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

onMounted(load);
</script>
