<template>
  <PageSection
    title="插件管理"
   
  >
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建插件</el-button>
      <el-button @click="pageLoad">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="pluginRows" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" min-width="5%" align="center" header-align="center" />
      <el-table-column prop="code" label="编码" min-width="12%" />
      <el-table-column prop="name" label="名称" min-width="15%" />
      <el-table-column prop="pluginType" label="类型" min-width="10%" />
      <el-table-column prop="version" label="版本" min-width="8%" />
      <el-table-column prop="status" label="状态" min-width="10%" />
      <el-table-column label="操作" min-width="15%" align="center" header-align="center">
        <template #default="{ row }">
          <el-select
            v-model="row._st"
            placeholder="切状态"
            size="small"
            style="width: 120px"
            @change="(v) => onStatus(row, v)"
          >
            <el-option label="ENABLED" value="ENABLED" />
            <el-option label="DISABLED" value="DISABLED" />
          </el-select>
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
        :page-sizes="[10, 20, 50]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="dlg" title="新建插件" width="480px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="编码" required><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型"><el-input v-model="form.pluginType" placeholder="PRE / POST" /></el-form-item>
        <el-form-item label="版本"><el-input v-model="form.version" /></el-form-item>
        <el-form-item label="状态"><el-input v-model="form.status" placeholder="DISABLED" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">创建</el-button>
      </template>
    </el-dialog>
  </PageSection>
</template>

<script setup>
import { reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import { usePageList } from "../composables/usePageList";
import { listPlugins, createPlugin, updatePluginStatus } from "../api/plugins";

const { rows, loading, total, pageNum, pageSize, load: pageLoad, onPageChange, onSizeChange, resetPage } = usePageList(listPlugins);
const pluginRows = ref([]);
const dlg = ref(false);
const saving = ref(false);
const form = reactive({ code: "", name: "", pluginType: "PRE", version: "0.0.1", status: "DISABLED" });

// 监听 rows 变化，同步到 pluginRows（附加 _st）
watch(rows, (list) => {
  pluginRows.value = list.map((r) => ({ ...r, _st: null }));
}, { immediate: true });

function openCreate() {
  form.code = "";
  form.name = "";
  form.pluginType = "PRE";
  form.version = "0.0.1";
  form.status = "DISABLED";
  dlg.value = true;
}

async function submit() {
  if (!form.code?.trim() || !form.name?.trim()) {
    ElMessage.warning("请填写编码与名称");
    return;
  }
  saving.value = true;
  try {
    await createPlugin({
      id: null,
      code: form.code.trim(),
      name: form.name.trim(),
      pluginType: form.pluginType,
      version: form.version,
      status: form.status
    });
    ElMessage.success("已创建");
    dlg.value = false;
    await resetPage();
  } finally {
    saving.value = false;
  }
}

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

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
