<template>
  <PageSection title="通知模板" description="维护通知模板编码、渠道与内容。">
    <div class="toolbar">
      <el-button type="primary" @click="openTpl">新建模板</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border size="small">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="code" label="编码" width="120" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="channel" label="渠道" width="90" />
      <el-table-column prop="content" label="内容模板" min-width="160" show-overflow-tooltip />
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

    <el-dialog v-model="tplDlg" title="新建模板" width="520px" destroy-on-close>
      <el-form :model="tplForm" label-width="100px">
        <el-form-item label="编码" required><el-input v-model="tplForm.code" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="tplForm.name" /></el-form-item>
        <el-form-item label="渠道"><el-input v-model="tplForm.channel" placeholder="EMAIL" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="tplForm.content" type="textarea" :rows="5" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tplDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitTpl">创建</el-button>
      </template>
    </el-dialog>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import { usePageList } from "../../composables/usePageList";
import { listNotifyTemplates, createNotifyTemplate } from "../../api/notifications";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } = usePageList(listNotifyTemplates);
const tplDlg = ref(false);
const saving = ref(false);
const tplForm = reactive({ code: "", name: "", channel: "EMAIL", content: "" });

function openTpl() {
  tplForm.code = "";
  tplForm.name = "";
  tplForm.channel = "EMAIL";
  tplForm.content = "";
  tplDlg.value = true;
}

async function submitTpl() {
  if (!tplForm.code?.trim() || !tplForm.name?.trim()) {
    ElMessage.warning("请填写编码与名称");
    return;
  }
  saving.value = true;
  try {
    await createNotifyTemplate({
      id: null,
      code: tplForm.code.trim(),
      name: tplForm.name.trim(),
      channel: tplForm.channel,
      content: tplForm.content
    });
    ElMessage.success("已创建");
    tplDlg.value = false;
    await resetPage();
  } finally {
    saving.value = false;
  }
}

load();
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
