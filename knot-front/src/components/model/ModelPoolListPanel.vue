<template>
  <div>
    <el-table v-loading="loading" :data="rows" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center" header-align="center" />
      <el-table-column prop="poolCode" label="模型池编码" min-width="150" show-overflow-tooltip />
      <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="模型类型" min-width="110">
        <template #default="{ row }">{{ modelTypeLabel(row.modelType) }}</template>
      </el-table-column>
      <el-table-column label="选择策略" min-width="110">
        <template #default="{ row }">{{ strategyLabel(row.selectionStrategy) }}</template>
      </el-table-column>
      <el-table-column label="模型数量" width="90" align="center">
        <template #default="{ row }">{{ row.items?.length || 0 }}</template>
      </el-table-column>
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
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="110" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'delete', label: '删除', icon: Delete, type: 'danger' }
            ]"
            @action="(action) => emit(action, row)"
          />
        </template>
      </el-table-column>
    </el-table>
    <ListPagination
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      @refresh="emit('refresh')"
      @page-change="(page) => emit('page-change', page)"
      @size-change="(size) => emit('size-change', size)"
    />
  </div>
</template>

<script setup>
import { onMounted } from "vue";
import { Delete, Edit } from "@element-plus/icons-vue";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";
import { updateModelPool } from "../../api/modelPools";
import { useEnabledToggle } from "../../composables/useEnabledToggle";
import { resolveEnumLabel, useEnums } from "../../composables/useEnums";

defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 }
});

const emit = defineEmits(["create", "refresh", "edit", "delete", "page-change", "size-change", "changed"]);

const { options: modelTypeOptions, loadOptions: loadModelTypes } = useEnums("model_type");
const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateModelPool,
  buildPayload: (row, enabled) => ({
    poolCode: row.poolCode,
    name: row.name,
    modelType: row.modelType,
    selectionStrategy: row.selectionStrategy || "WEIGHTED",
    enabled,
    remark: row.remark ?? null,
    items: row.items || []
  })
});

function modelTypeLabel(code) {
  return resolveEnumLabel(modelTypeOptions.value, code, code || "—");
}

function strategyLabel(code) {
  const map = { WEIGHTED: "权重", PRIORITY: "优先级", RANDOM: "随机" };
  return map[code] || code || "—";
}

async function handleEnabledChange(row, enabled) {
  await onEnabledChange(row, enabled);
  emit("changed");
}

onMounted(loadModelTypes);
</script>
