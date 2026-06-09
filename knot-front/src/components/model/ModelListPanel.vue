<template>
  <div>
    <el-table v-loading="loading" :data="rows" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center" header-align="center" />
      <el-table-column prop="modelCode" label="模型编码" min-width="140" show-overflow-tooltip />
      <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="供应商" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.providerName || (row.providerId != null ? `#${row.providerId}` : "-") }}
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
            @change="(val) => handleEnabledChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'copy', label: '复制', icon: CopyDocument },
              { key: 'log', label: '日志', icon: Document }
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
      :show-refresh="showRefresh"
      @refresh="emit('refresh')"
      @page-change="(page) => emit('page-change', page)"
      @size-change="(size) => emit('size-change', size)"
    />
  </div>
</template>

<script setup>
import { onMounted } from "vue";
import { CopyDocument, Document, Edit } from "@element-plus/icons-vue";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";
import { updateModelStatus } from "@/api/models.js";
import { useEnabledToggle } from "@/composables/useEnabledToggle.js";
import { useEnums } from "@/composables/useEnums.js";

defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  showRefresh: { type: Boolean, default: true }
});

const emit = defineEmits(["create", "refresh", "edit", "copy", "log", "page-change", "size-change", "changed"]);

const { options: modelTypeOptions, loadOptions: loadModelTypes } = useEnums("model_type");

const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateModelStatus
});

function modelTypeLabel(code) {
  if (!code) return "-";
  const item = modelTypeOptions.value.find((i) => i.itemCode === code);
  return item?.itemLabel || code;
}

function handleAction(action, row) {
  emit(action, row);
}

async function handleEnabledChange(row, enabled) {
  await onEnabledChange(row, enabled);
  emit("changed");
}

onMounted(() => {
  loadModelTypes();
});
</script>
