<template>
  <PageSection title="计费规则" description="维护计费规则编码、名称、单位与单价。">
    <div class="toolbar">
      <el-button type="primary" @click="openRule">新建规则</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border size="small">
      <el-table-column prop="code" label="编码" width="120" />
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="unit" label="单位" width="90" />
      <el-table-column prop="unitPrice" label="单价" width="100" />
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

    <el-dialog v-model="ruleDlg" title="新建计费规则" width="480px" destroy-on-close>
      <el-form :model="ruleForm" label-width="90px">
        <el-form-item label="编码" required><el-input v-model="ruleForm.code" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="ruleForm.name" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="ruleForm.unit" placeholder="1K tokens" /></el-form-item>
        <el-form-item label="单价"><el-input-number v-model="ruleForm.unitPrice" :min="0" :step="0.0001" :precision="6" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRule">创建</el-button>
      </template>
    </el-dialog>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import { usePageList } from "../../composables/usePageList";
import { listBillingRules, createBillingRule } from "../../api/billing";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } = usePageList(listBillingRules);
const ruleDlg = ref(false);
const saving = ref(false);
const ruleForm = reactive({ code: "", name: "", unit: "1K tokens", unitPrice: 0.002 });

function openRule() {
  ruleForm.code = "";
  ruleForm.name = "";
  ruleForm.unit = "1K tokens";
  ruleForm.unitPrice = 0.002;
  ruleDlg.value = true;
}

async function submitRule() {
  if (!ruleForm.code?.trim() || !ruleForm.name?.trim()) {
    ElMessage.warning("请填写编码与名称");
    return;
  }
  saving.value = true;
  try {
    await createBillingRule({
      code: ruleForm.code.trim(),
      name: ruleForm.name.trim(),
      unit: ruleForm.unit,
      unitPrice: ruleForm.unitPrice
    });
    ElMessage.success("已创建");
    ruleDlg.value = false;
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
