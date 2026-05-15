<template>
  <PageSection title="枚举管理">
    <div class="toolbar">
      <el-button type="primary" @click="openCreateCategory">新增分类首项</el-button>
      <el-button @click="loadSummaries">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="summaries" stripe border size="small">
      <el-table-column prop="category" label="分类编码" min-width="160" show-overflow-tooltip />
      <el-table-column prop="itemCount" label="枚举项数" width="110" align="center" />
      <el-table-column prop="enabledCount" label="已启用" width="100" align="center" />
      <el-table-column label="操作" width="220" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openItemsDrawer(row.category)">枚举管理</el-button>
          <el-button link type="primary" @click="openChangeLog(row.category)">变更日志</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 某分类下的枚举值列表与维护 -->
    <el-drawer v-model="itemsDrawer" :title="`枚举值 — ${currentCategory || ''}`" size="720px" destroy-on-close @closed="onItemsDrawerClosed">
      <div class="drawer-toolbar">
        <el-button type="primary" size="small" @click="openCreateItem">新增枚举值</el-button>
        <el-button size="small" @click="reloadItems">刷新</el-button>
      </div>
      <el-table v-loading="itemsLoading" :data="items" stripe border size="small">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="itemCode" label="编码" width="140" show-overflow-tooltip />
        <el-table-column prop="itemLabel" label="显示名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="72" align="center" />
        <el-table-column label="系统" width="72" align="center">
          <template #default="{ row }">
            <StatusTag :active="row.isSystem" />
          </template>
        </el-table-column>
        <el-table-column label="启用" width="72" align="center">
          <template #default="{ row }">
            <StatusTag :active="row.isEnabled" />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.isSystem" @click="openEditItem(row)">编辑</el-button>
            <el-button link type="danger" :disabled="row.isSystem" @click="onDeleteItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 新增/编辑枚举项 -->
    <el-dialog v-model="formDlg.visible" :title="formDlg.isEdit ? '编辑枚举值' : '新增枚举值'" width="520px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="分类" required>
          <el-input v-if="formDlg.isEdit" :model-value="form.category" disabled />
          <el-input v-else v-model="form.category" placeholder="如 provider_type" />
        </el-form-item>
        <el-form-item label="编码" required>
          <el-input v-if="formDlg.isEdit" :model-value="form.itemCode" disabled />
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
        <el-button @click="formDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分类变更日志 -->
    <el-drawer v-model="logDrawer" :title="`枚举变更日志 — ${logCategory || ''}`" size="640px" destroy-on-close>
      <el-table v-loading="logsLoading" :data="changeLogs" stripe border size="small" max-height="calc(100vh - 140px)">
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="operation" label="操作" width="90" />
        <el-table-column prop="entityName" label="对象" min-width="140" show-overflow-tooltip />
        <el-table-column prop="status" label="结果" width="88">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="120" show-overflow-tooltip />
        <el-table-column prop="errorMsg" label="错误" min-width="120" show-overflow-tooltip />
      </el-table>
    </el-drawer>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import StatusTag from "../../components/common/StatusTag.vue";
import { clearEnumCache } from "../../composables/useEnums";
import {
  listEnumCategorySummaries,
  listEnumItemsByCategory,
  listEnumOperationLogs,
  createEnumConfig,
  updateEnumConfig,
  deleteEnumConfig
} from "../../api/enums";

const loading = ref(false);
const summaries = ref([]);

const itemsDrawer = ref(false);
const currentCategory = ref("");
const itemsLoading = ref(false);
const items = ref([]);

const formDlg = reactive({ visible: false, isEdit: false });
const saving = ref(false);
const form = reactive({
  id: null,
  category: "",
  itemCode: "",
  itemLabel: "",
  sortOrder: 0,
  isEnabled: true,
  remark: ""
});

const logDrawer = ref(false);
const logCategory = ref("");
const logsLoading = ref(false);
const changeLogs = ref([]);

async function loadSummaries() {
  loading.value = true;
  try {
    const data = await listEnumCategorySummaries();
    summaries.value = Array.isArray(data) ? data : [];
  } finally {
    loading.value = false;
  }
}

async function openItemsDrawer(category) {
  currentCategory.value = category;
  itemsDrawer.value = true;
  await reloadItems();
}

async function reloadItems() {
  if (!currentCategory.value) return;
  itemsLoading.value = true;
  try {
    const data = await listEnumItemsByCategory(currentCategory.value);
    items.value = Array.isArray(data) ? data : [];
  } finally {
    itemsLoading.value = false;
  }
}

function onItemsDrawerClosed() {
  items.value = [];
  currentCategory.value = "";
}

function openCreateCategory() {
  formDlg.isEdit = false;
  form.id = null;
  form.category = "";
  form.itemCode = "";
  form.itemLabel = "";
  form.sortOrder = 0;
  form.isEnabled = true;
  form.remark = "";
  formDlg.visible = true;
}

function openCreateItem() {
  if (!currentCategory.value) {
    ElMessage.warning("请先选择分类");
    return;
  }
  formDlg.isEdit = false;
  form.id = null;
  form.category = currentCategory.value;
  form.itemCode = "";
  form.itemLabel = "";
  form.sortOrder = 0;
  form.isEnabled = true;
  form.remark = "";
  formDlg.visible = true;
}

function openEditItem(row) {
  formDlg.isEdit = true;
  form.id = row.id;
  form.category = row.category;
  form.itemCode = row.itemCode;
  form.itemLabel = row.itemLabel;
  form.sortOrder = row.sortOrder ?? 0;
  form.isEnabled = row.isEnabled !== false;
  form.remark = row.remark || "";
  formDlg.visible = true;
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
    if (formDlg.isEdit) {
      await updateEnumConfig(form.id, payload);
      ElMessage.success("已保存");
    } else {
      await createEnumConfig(payload);
      ElMessage.success("已创建");
    }
    formDlg.visible = false;
    clearEnumCache();
    const affectedCat = (formDlg.isEdit ? form.category : payload.category).trim();
    await loadSummaries();
    if (itemsDrawer.value && currentCategory.value === affectedCat) {
      await reloadItems();
    }
  } finally {
    saving.value = false;
  }
}

async function onDeleteItem(row) {
  await ElMessageBox.confirm(`确认删除枚举「${row.itemLabel}」(${row.category}/${row.itemCode})？`, "删除确认", { type: "warning" });
  await deleteEnumConfig(row.id);
  ElMessage.success("已删除");
  clearEnumCache();
  await loadSummaries();
  await reloadItems();
}

async function openChangeLog(category) {
  logCategory.value = category;
  logDrawer.value = true;
  logsLoading.value = true;
  try {
    const data = await listEnumOperationLogs(category);
    changeLogs.value = Array.isArray(data) ? data : [];
  } finally {
    logsLoading.value = false;
  }
}

loadSummaries();
</script>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
.drawer-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
