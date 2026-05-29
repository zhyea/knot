<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="emit('create')">新建规则</el-button>
      <el-button @click="emit('refresh')">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border size="small" style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="code" label="编码" width="140" show-overflow-tooltip />
      <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
      <el-table-column label="供应商" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.providerName || (row.providerId ? `#${row.providerId}` : "全局") }}</template>
      </el-table-column>
      <el-table-column label="统一模型" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.logicalModelName || (row.logicalModelId ? `#${row.logicalModelId}` : "默认") }}</template>
      </el-table-column>
      <el-table-column label="版本" width="90" align="center">
        <template #default="{ row }">v{{ row.versionNo || 1 }}</template>
      </el-table-column>
      <el-table-column label="计费模式" width="130">
        <template #default="{ row }">{{ billingModeLabel(row.billingMode) }}</template>
      </el-table-column>
      <el-table-column prop="currency" label="币种" width="80" align="center" />
      <el-table-column prop="unit" label="单位" width="110" />
      <el-table-column prop="unitPrice" label="单价" width="110" />
      <el-table-column label="启用" width="88" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled !== false"
            :loading="togglingId === row.id"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
            @change="(val) => emit('enabled-change', row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'log', label: '日志', icon: Document },
              { key: 'delete', label: '删除', icon: Delete, type: 'danger', confirm: '删除后不可在列表中查看，确认删除？' }
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
import { Delete, Document, Edit } from "@element-plus/icons-vue";
import RowActions from "../common/RowActions.vue";
import { useEnums } from "../../composables/useEnums";

defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  togglingId: { type: [Number, String], default: null }
});

const emit = defineEmits(["create", "edit", "log", "delete", "refresh", "enabled-change", "page-change", "size-change"]);
const { options: billingModeOptions, loadOptions: loadBillingModes } = useEnums("billing_mode");

function billingModeLabel(code) {
  if (!code) return "-";
  const item = billingModeOptions.value.find((i) => i.itemCode === code);
  return item?.itemLabel || code;
}

function handleAction(action, row) {
  emit(action, row);
}

onMounted(() => {
  loadBillingModes();
});
</script>
