<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按族编码、名称筛选"
          @query="handleQuery"
        />
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建模型族</el-button>
          </div>
        </div>

        <el-table v-loading="loading" :data="rows" stripe border style="width: 100%">
          <el-table-column prop="id" label="ID" width="70" align="center" header-align="center" fixed="left"/>
          <el-table-column prop="code" label="族编码" min-width="140" show-overflow-tooltip />
          <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
          <el-table-column label="引用数" width="90" align="center">
            <template #default="{ row }">
              <span :class="{ 'usage-active': (row.usageCount || 0) > 0 }">{{ row.usageCount || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
          <el-table-column label="启用" width="88" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="row.enabled !== false"
                :loading="togglingId === row.id"
                inline-prompt
                active-text="启用"
                inactive-text="禁用"
                @change="(value) => onEnabledChange(row, value)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="120" align="center" header-align="center" fixed="right">
            <template #default="{ row }">
              <RowActions
                :actions="[
                  { key: 'edit', label: '编辑', icon: Edit },
                  { key: 'delete', label: '删除', icon: Delete, type: 'danger' }
                ]"
                @action="(action) => handleAction(action, row)"
              />
            </template>
          </el-table-column>
        </el-table>

        <ListPagination
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :show-refresh="false"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <ModelFamilyFormDialog v-model="formVisible" :family="editingFamily" @saved="resetPage" />
  </PageSection>
</template>

<script setup lang="ts">
import type {Dict, Row} from "@/types";
import {onMounted, ref} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import {Delete, Edit} from "@element-plus/icons-vue";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import ListPagination from "../../components/common/ListPagination.vue";
import RowActions from "../../components/common/RowActions.vue";
import ModelFamilyFormDialog from "../../components/model/ModelFamilyFormDialog.vue";
import {useListQuery} from "@/composables/useListQuery";
import {useEnabledToggle} from "@/composables/useEnabledToggle";
import {deleteModelFamily, listModelFamilies, updateModelFamilyStatus} from "@/api/modelFamilies";

/**
 * 模型族主数据存于枚举表 ks_enum_configs（category='model_family'），与「系统管理 / 枚举管理」
 * 同源；本页是模型域的独立维护入口，权限为 model:model-family:*。
 * 引用数 = 统一模型归类 + 计费规则作用域引用条数，大于 0 时后端拒绝删除。
 */
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
} = useListQuery({
  apiFn: (params: Dict) => listModelFamilies(params),
  fields: { keyword: "" }
});

const formVisible = ref(false);
const editingFamily = ref<Row | null>(null);

const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateModelFamilyStatus
});

function openCreate() {
  editingFamily.value = null;
  formVisible.value = true;
}

function openEdit(row: Row) {
  editingFamily.value = row;
  formVisible.value = true;
}

async function remove(row: Row) {
  await ElMessageBox.confirm(`确认删除模型族“${row.name || row.code}”？`, "删除确认", {
    type: "warning"
  });
  await deleteModelFamily(row.id);
  ElMessage.success("已删除");
  resetPage();
}

function handleAction(action: string, row: Row) {
  if (action === "edit") openEdit(row);
  if (action === "delete") remove(row);
}

onMounted(load);
</script>

<style scoped>
.usage-active {
  color: var(--el-color-primary);
  font-weight: 600;
}
</style>
