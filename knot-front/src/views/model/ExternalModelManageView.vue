<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按模型名、模型 ID、供应商筛选"
          @query="handleQuery"
        />
        <FilterField label="来源" :width="180">
          <el-select
            v-model="query.sourceCode"
            placeholder="来源"
            clearable
          >
            <el-option
              v-for="source in sources"
              :key="source.sourceCode"
              :label="source.sourceName"
              :value="source.sourceCode"
            />
          </el-select>
        </FilterField>
        <FilterField label="模型类型" :width="260">
          <EnumControl
            v-model="query.modelType"
            enum-name="ModelTypeEnum"
            clearable
          />
        </FilterField>
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button-group class="external-model-sync-group">
              <el-button type="primary" :loading="syncing" @click="syncCurrentSource">
                {{ syncButtonText }}
              </el-button>
              <el-dropdown trigger="click" @command="setSyncSource">
                <el-button type="primary" aria-label="选择要同步的来源">
                  <el-icon><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="source in sources"
                      :key="source.sourceCode"
                      :command="source.sourceCode"
                      :icon="source.sourceCode === syncSourceCode ? Check : undefined"
                    >
                      {{ source.sourceName }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </el-button-group>
            <el-button
              type="success"
              :loading="creatingAll"
              :disabled="createAllDisabled"
              @click="createAllVisible"
            >
              一键创建统一模型
            </el-button>
            <el-popconfirm title="确认物理删除选中的外部模型？" @confirm="deleteSelected">
              <template #reference>
                <el-button type="danger" :disabled="!selectedRows.length">批量删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>

        <ExternalModelListPanel
          :rows="rows"
          :sources="sources"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :show-refresh="false"
          @selection-change="handleSelectionChange"
          @action="handleAction"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <ExternalModelDetailDrawer v-model="detailVisible" :detail="detail" />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`外部模型操作日志 - ${logName || ''}`"
      :load-logs="loadExternalLogs"
    />
  </PageSection>
</template>

<script setup lang="ts">
import type {Dict, Row} from "@/types";
import {computed, onMounted, ref} from "vue";
import {ElMessage} from "element-plus";
import {ArrowDown, Check} from "@element-plus/icons-vue";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import {useListQuery} from "@/composables/useListQuery";
import EnumControl from "../../components/common/EnumControl.vue";
import ExternalModelDetailDrawer from "../../components/model/ExternalModelDetailDrawer.vue";
import ExternalModelListPanel from "../../components/model/ExternalModelListPanel.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import {listOperationLogsByEntity} from "@/api/operationLogs";
import {useLogDrawer} from "@/composables/useLogDrawer";
const {id: logId, name: logName, visible: logDrawer, open: openLog} = useLogDrawer({labelFields: ["modelName", "modelId"]});
import {
  createLogicalModelFromExternalItem,
  createLogicalModelsFromExternalItems,
  deleteExternalModelItem,
  deleteExternalModelItems,
  getExternalModelItem,
  listExternalModelItems,
  listExternalModelSources,
  setExternalModelIgnored,
  syncExternalModelSource
} from "@/api/externalModels";

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
} = useListQuery({ apiFn: listExternalModelItems, fields: { sourceCode: "", keyword: "", modelType: "" } });

const sources = ref<Row[]>([]);
// 要同步哪个源由这里决定，与列表筛选彻底解耦（曾把同步对象绑在筛选条件上，来源切换被迫借道过滤，很蠢）
const syncSourceCode = ref("");
const syncing = ref(false);
const creatingAll = ref(false);
const detailVisible = ref(false);
const detail = ref<Dict | null>(null);
const selectedRows = ref<Row[]>([]);


const createAllDisabled = computed(() => creatingAll.value || selectedRows.value.length === 0);

// 同步按钮对「下拉里选中的源」生效；来源清单来自 listSources()，新增外部数据源时这里不用改代码
const syncButtonText = computed(() => {
  const hit = sources.value.find((source: Row) => source.sourceCode === syncSourceCode.value);
  return `同步 ${hit?.sourceName || syncSourceCode.value || "…"} 模型`;
});

async function loadSources() {
  sources.value = await listExternalModelSources();
  if (!syncSourceCode.value && sources.value.length) {
    syncSourceCode.value = sources.value[0].sourceCode;
  }
}

function setSyncSource(code: string) {
  syncSourceCode.value = code;
}

async function syncCurrentSource() {
  const code = syncSourceCode.value;
  if (!code) {
    ElMessage.warning("请先在下拉中选择要同步的来源");
    return;
  }
  syncing.value = true;
  try {
    const result = await syncExternalModelSource(code);
    ElMessage.success(result?.message || "同步完成");
    selectedRows.value = [];
    await resetPage();
  } finally {
    syncing.value = false;
  }
}

async function handleAction(action: string, row: Row) {
  if (action === "view") await openDetail(row);
  if (action === "create") await createOne(row);
  if (action === "delete") await deleteOne(row);
  if (action === "ignore" || action === "unignore") await toggleIgnored(row);
  if (action === "log") openLog(row);
}

function loadExternalLogs() {
  return listOperationLogsByEntity({ module: "external-model", entityType: "ExternalModelItem", entityId: logId.value! });
}

async function toggleIgnored(row: Row) {
  const ignored = !Boolean(row.ignored);
  await setExternalModelIgnored(row.id, ignored);
  ElMessage.success(ignored ? "已忽略外部模型" : "已解除忽略");
  selectedRows.value = [];
  await resetPage();
}

function handleSelectionChange(selection: Row[]) {
  selectedRows.value = selection;
}

async function openDetail(row: Row) {
  detail.value = await getExternalModelItem(row.id);
  detailVisible.value = true;
}

async function createOne(row: Row) {
  await createLogicalModelFromExternalItem(row.id);
  ElMessage.success("已创建统一模型");
  selectedRows.value = [];
  await resetPage();
}

async function createAllVisible() {
  const ids = selectedRows.value.map((row) => row.id).filter((id) => id != null);
  if (!ids.length) {
    ElMessage.warning("请选择要创建统一模型的外部模型");
    return;
  }
  creatingAll.value = true;
  try {
    const result = await createLogicalModelsFromExternalItems({
      ids
    });
    ElMessage.success(result?.message || `已创建 ${result?.inserted || 0} 个统一模型`);
    selectedRows.value = [];
    await resetPage();
  } finally {
    creatingAll.value = false;
  }
}

async function deleteOne(row: Row) {
  await deleteExternalModelItem(row.id);
  ElMessage.success("已删除外部模型");
  selectedRows.value = [];
  await resetPage();
}

async function deleteSelected() {
  const ids = selectedRows.value.map((row) => row.id);
  if (!ids.length) {
    ElMessage.warning("请选择要删除的外部模型");
    return;
  }
  const affected = await deleteExternalModelItems(ids);
  ElMessage.success(`已删除 ${affected || ids.length} 个外部模型`);
  selectedRows.value = [];
  await resetPage();
}


onMounted(() => {
  loadSources();
  load();
});
</script>
