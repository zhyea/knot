<template>
  <PageSection
    title="插件管理"
    description="注册网关插件、维护类型与版本，并切换启用状态。"
  >
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建插件</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="code" label="编码" width="120" />
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="pluginType" label="类型" width="100" />
      <el-table-column prop="version" label="版本" width="90" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="200" fixed="right">
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
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import { listPlugins, createPlugin, updatePluginStatus } from "../api/plugins";

const rows = ref([]);
const loading = ref(false);
const dlg = ref(false);
const saving = ref(false);
const form = reactive({ code: "", name: "", pluginType: "PRE", version: "0.0.1", status: "DISABLED" });

async function load() {
  loading.value = true;
  try {
    const list = await listPlugins();
    rows.value = list.map((r) => ({ ...r, _st: null }));
  } finally {
    loading.value = false;
  }
}

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
    await load();
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
    await load();
  } catch {
    row._st = null;
  }
}

load();
</script>
