<template>
  <PageSection title="枚举管理">
    <div class="toolbar">
      <el-select v-model="filterCategory" placeholder="按分类筛选" clearable style="width: 200px" @change="onFilter">
        <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
      </el-select>
      <el-button type="primary" @click="openCreate">新增枚举</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border size="small">
      <el-table-column prop="id" label="ID" width="70" align="center" header-align="center" />
      <el-table-column prop="category" label="分类" width="150" />
      <el-table-column prop="itemCode" label="编码" width="150" />
      <el-table-column prop="itemLabel" label="显示名" min-width="120" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="系统" width="70">
        <template #default="{ row }">
          <StatusTag :active="row.isSystem" />
        </template>
      </el-table-column>
      <el-table-column label="启用" width="70">
        <template #default="{ row }">
          <StatusTag :active="row.isEnabled" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="140" align="center" header-align="center">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="row.isSystem" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" :disabled="row.isSystem" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination-wrap">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        :page-sizes="[20, 50, 100]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="dlg.visible" :title="dlg.isEdit ? '编辑枚举' : '新增枚举'" width="520px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="分类" required>
          <el-input v-if="dlg.isEdit" :model-value="form.category" disabled />
          <el-input v-else v-model="form.category" placeholder="如 provider_type" />
        </el-form-item>
        <el-form-item label="编码" required>
          <el-input v-if="dlg.isEdit" :model-value="form.itemCode" disabled />
          <el-input v-else v-model="form.itemCode" placeholder="如 OPENAI" />
        </el-form-item>
        <el-form-item label="显示名" required>
          <el-input v-model="form.itemLabel" placeholder="如 OpenAI" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.isEnabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </PageSection>
</template>

<script setup>
import { reactive, ref, computed } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import StatusTag from "../../components/common/StatusTag.vue";
import { usePageList } from "../../composables/usePageList";
import { useEnumCategories } from "../../composables/useEnums";
import { clearEnumCache } from "../../composables/useEnums";
import {
  listEnumConfigs,
  createEnumConfig,
  updateEnumConfig,
  deleteEnumConfig
} from "../../api/enums";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage, extra: queryExtra } = usePageList(listEnumConfigs, { pageSize: 20, extra: { category: '' } });
const { categories, loadCategories } = useEnumCategories();

const filterCategory = computed({
  get: () => queryExtra.category || '',
  set: (v) => { queryExtra.category = v; }
});

function onFilter() {
  resetPage();
}

const saving = ref(false);
const dlg = reactive({ visible: false, isEdit: false });
const form = reactive({
  id: null,
  category: "",
  itemCode: "",
  itemLabel: "",
  sortOrder: 0,
  isEnabled: true,
  remark: ""
});

function openCreate() {
  dlg.isEdit = false;
  form.id = null;
  form.category = "";
  form.itemCode = "";
  form.itemLabel = "";
  form.sortOrder = 0;
  form.isEnabled = true;
  form.remark = "";
  dlg.visible = true;
}

function openEdit(row) {
  dlg.isEdit = true;
  form.id = row.id;
  form.category = row.category;
  form.itemCode = row.itemCode;
  form.itemLabel = row.itemLabel;
  form.sortOrder = row.sortOrder;
  form.isEnabled = row.isEnabled;
  form.remark = row.remark || "";
  dlg.visible = true;
}

async function submitForm() {
  if (!form.category?.trim() || !form.itemCode?.trim() || !form.itemLabel?.trim()) {
    ElMessage.warning("请填写分类、编码和显示名");
    return;
  }
  saving.value = true;
  try {
    const payload = {
      category: form.category.trim(),
      itemCode: form.itemCode.trim(),
      itemLabel: form.itemLabel.trim(),
      sortOrder: form.sortOrder,
      isEnabled: form.isEnabled,
      remark: form.remark
    };
    if (dlg.isEdit) {
      await updateEnumConfig(form.id, payload);
      ElMessage.success("已保存");
    } else {
      await createEnumConfig(payload);
      ElMessage.success("已创建");
    }
    dlg.visible = false;
    clearEnumCache();
    await resetPage();
  } finally {
    saving.value = false;
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除枚举「${row.itemLabel}」(${row.category}/${row.itemCode})？`, "删除确认", { type: "warning" });
  await deleteEnumConfig(row.id);
  ElMessage.success("已删除");
  clearEnumCache();
  await resetPage();
}

loadCategories();
load();
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
