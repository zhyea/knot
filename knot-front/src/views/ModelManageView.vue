<template>
  <PageSection title="模型管理">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建模型</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center" header-align="center" />
      <el-table-column prop="modelCode" label="模型编码" min-width="140" show-overflow-tooltip />
      <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="供应商" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.providerName || (row.providerId != null ? `#${row.providerId}` : "—") }}
        </template>
      </el-table-column>
      <el-table-column label="类型" min-width="100">
        <template #default="{ row }">
          {{ modelTypeLabel(row.modelType) }}
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" min-width="110" show-overflow-tooltip />
      <el-table-column label="启用" width="88" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled !== false"
            :loading="togglingId === row.id"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
            @change="(val) => onEnabledChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openTest(row)">测试</el-button>
          <el-button link type="primary" @click="openSwitch(row)">切版本</el-button>
          <el-button link type="primary" @click="openChangeLog(row)">日志</el-button>
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

    <ModelFormDrawer v-model="formVisible" :model="editingModel" @saved="onFormSaved" />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`模型变更日志 — ${logModelName || ''}`"
      :load-logs="loadModelOperationLogs"
    />

    <el-dialog v-model="testDlg" title="模型联调测试" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="Prompt">
          <el-input v-model="testForm.prompt" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="温度">
          <el-input-number v-model="testForm.temperature" :step="0.1" :min="0" :max="2" />
        </el-form-item>
        <el-form-item label="Max tokens">
          <el-input-number v-model="testForm.maxTokens" :min="1" />
        </el-form-item>
      </el-form>
      <el-alert v-if="testResult" type="success" :closable="false" class="mt">
        <template #title>输出（演示）</template>
        <div>{{ testResult.output }}</div>
        <div class="sub">延迟 {{ testResult.latencyMs }} ms · token {{ testResult.tokenUsage }}</div>
      </el-alert>
      <template #footer>
        <el-button @click="testDlg = false">关闭</el-button>
        <el-button type="primary" :loading="testLoading" @click="runTest">发送</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="swDlg" title="切换活跃版本" width="400px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="目标版本">
          <el-input v-model="swVersion" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="swDlg = false">取消</el-button>
        <el-button type="primary" :loading="swLoading" @click="runSwitch">确定</el-button>
      </template>
    </el-dialog>
  </PageSection>
</template>

<script setup>
import { ref, reactive, onMounted } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import ModelFormDrawer from "../components/model/ModelFormDrawer.vue";
import OperationLogDrawer from "../components/common/OperationLogDrawer.vue";
import { listModelOperationLogs } from "../api/operationLogs";
import { usePageList } from "../composables/usePageList";
import { useEnabledToggle } from "../composables/useEnabledToggle";
import { useEnums } from "../composables/useEnums";
import { listModels, updateModel, testModel, switchModelVersion } from "../api/models";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listModels);

const { options: modelTypeOptions, loadOptions: loadModelTypes } = useEnums("model_type");

function modelTypeLabel(code) {
  if (!code) return "—";
  const item = modelTypeOptions.value.find((i) => i.itemCode === code);
  return item?.itemLabel || code;
}

const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateModel,
  buildPayload: (row, enabled) => ({
    name: row.name,
    providerId: row.providerId,
    modelType: row.modelType,
    version: row.version,
    enabled,
    rateLimitPolicy: row.rateLimitPolicy ?? null,
    quotaPolicy: row.quotaPolicy ?? null
  })
});

const formVisible = ref(false);
const editingModel = ref(null);

const logDrawer = ref(false);
const logModelId = ref(null);
const logModelName = ref("");

const testDlg = ref(false);
const testLoading = ref(false);
const testId = ref(null);
const testForm = reactive({ prompt: "hello", temperature: 0.7, maxTokens: 256 });
const testResult = ref(null);

const swDlg = ref(false);
const swLoading = ref(false);
const swId = ref(null);
const swVersion = ref("");

function openCreate() {
  editingModel.value = null;
  formVisible.value = true;
}

function openEdit(row) {
  editingModel.value = row;
  formVisible.value = true;
}

function onFormSaved() {
  resetPage();
}

function openChangeLog(row) {
  logModelId.value = row.id;
  logModelName.value = row.name || row.modelCode || `#${row.id}`;
  logDrawer.value = true;
}

function loadModelOperationLogs() {
  return listModelOperationLogs(logModelId.value);
}

function openTest(row) {
  testId.value = row.id;
  testResult.value = null;
  testDlg.value = true;
}

async function runTest() {
  testLoading.value = true;
  try {
    testResult.value = await testModel(testId.value, {
      prompt: testForm.prompt,
      temperature: testForm.temperature,
      maxTokens: testForm.maxTokens
    });
  } finally {
    testLoading.value = false;
  }
}

function openSwitch(row) {
  swId.value = row.id;
  swVersion.value = row.version || "";
  swDlg.value = true;
}

async function runSwitch() {
  if (!swVersion.value?.trim()) {
    ElMessage.warning("请填写版本号");
    return;
  }
  swLoading.value = true;
  try {
    await switchModelVersion(swId.value, { targetVersion: swVersion.value.trim() });
    ElMessage.success("已切换");
    swDlg.value = false;
    await resetPage();
  } finally {
    swLoading.value = false;
  }
}

onMounted(() => {
  loadModelTypes();
  load();
});
</script>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.mt {
  margin-top: 12px;
}

.sub {
  margin-top: 6px;
  font-size: 12px;
  color: #606266;
}
</style>
