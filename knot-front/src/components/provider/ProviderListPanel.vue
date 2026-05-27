<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="emit('create')">新建供应商</el-button>
      <el-button @click="emit('refresh')">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center" header-align="center"/>
      <el-table-column prop="code" label="编码" min-width="10%" show-overflow-tooltip />
      <el-table-column prop="name" label="名称" min-width="15%"/>
      <el-table-column label="类型" min-width="10%" show-overflow-tooltip>
        <template #default="{ row }">
          {{ typeLabel(row.type) }}
        </template>
      </el-table-column>
      <el-table-column prop="baseUrl" label="Base URL" min-width="28%" show-overflow-tooltip/>
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
      <el-table-column label="操作" width="130" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'discount', label: '折扣策略', icon: Discount },
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
          @current-change="(p) => emit('page-change', p)"
          @size-change="(s) => emit('size-change', s)"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted } from "vue";
import { Discount, Document, Edit } from "@element-plus/icons-vue";
import RowActions from "../common/RowActions.vue";
import { updateProvider } from "../../api/providers";
import { useEnabledToggle } from "../../composables/useEnabledToggle";
import { useEnums } from "../../composables/useEnums";

defineProps({
  rows: {type: Array, default: () => []},
  loading: {type: Boolean, default: false},
  total: {type: Number, default: 0},
  pageNum: {type: Number, default: 1},
  pageSize: {type: Number, default: 20}
});

const emit = defineEmits(["create", "refresh", "edit", "discount", "log", "page-change", "size-change", "changed"]);

const { options: providerTypeOptions, loadOptions: loadProviderTypes } = useEnums("provider_type");

function typeLabel(code) {
  if (!code) return "—";
  const item = providerTypeOptions.value.find((i) => i.itemCode === code);
  return item?.itemLabel || code;
}

onMounted(() => {
  loadProviderTypes();
});

const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateProvider,
  buildPayload: (row, enabled) => ({
    code: row.code,
    name: row.name,
    type: row.type,
    baseUrl: row.baseUrl,
    enabled,
    authConfig: row.authConfig ?? null,
    rateLimitPolicy: row.rateLimitPolicy ?? null,
    quotaPolicy: row.quotaPolicy ?? null
  })
});

function handleAction(action, row) {
  if (action === "edit") emit("edit", row);
  if (action === "discount") emit("discount", row);
  if (action === "log") emit("log", row);
}

async function handleEnabledChange(row, enabled) {
  await onEnabledChange(row, enabled);
  emit("changed");
}
</script>
