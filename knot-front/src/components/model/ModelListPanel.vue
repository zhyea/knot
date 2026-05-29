<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="emit('create')">新建模型</el-button>
      <el-button @click="emit('refresh')">刷新</el-button>
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
            @change="(val) => handleEnabledChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'test', label: '测试', icon: VideoPlay },
              { key: 'switch', label: '切版本', icon: RefreshRight },
              { key: 'log', label: '日志', icon: Document }
            ]"
            @action="(action) => handleAction(action, row)"
          />
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
        @current-change="(page) => emit('page-change', page)"
        @size-change="(size) => emit('size-change', size)"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted } from "vue";
import { Document, Edit, RefreshRight, VideoPlay } from "@element-plus/icons-vue";
import RowActions from "../common/RowActions.vue";
import { updateModel } from "../../api/models";
import { useEnabledToggle } from "../../composables/useEnabledToggle";
import { useEnums } from "../../composables/useEnums";

defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 }
});

const emit = defineEmits(["create", "refresh", "edit", "test", "switch", "log", "page-change", "size-change", "changed"]);

const { options: modelTypeOptions, loadOptions: loadModelTypes } = useEnums("model_type");

const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateModel,
  buildPayload: (row, enabled) => ({
    name: row.name,
    providerId: row.providerId,
    logicalModelId: row.logicalModelId,
    billingRuleId: row.billingRuleId ?? null,
    modelType: row.modelType,
    version: row.version,
    enabled,
    rateLimitPolicy: row.rateLimitPolicy ?? null,
    quotaPolicy: row.quotaPolicy ?? null
  })
});

function modelTypeLabel(code) {
  if (!code) return "—";
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
