<template>
  <div>
    <el-table v-loading="loading" :data="rows" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center" header-align="center"/>
      <el-table-column prop="code" label="编码" min-width="100" show-overflow-tooltip/>
      <el-table-column prop="providerName" label="供应商" min-width="100" show-overflow-tooltip/>
      <el-table-column label="创建时间" min-width="80" align="center" show-overflow-tooltip>
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="80" align="center" show-overflow-tooltip>
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="启用" width="88" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled !== false"
            :loading="togglingId === row.id"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
            @change="(value) => handleEnabledChange(row, value)"
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
import {Discount, Document, Edit} from "@element-plus/icons-vue";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";
import {updateProviderStatus} from "@/api/providers.js";
import {useEnabledToggle} from "@/composables/useEnabledToggle.js";
import {formatDateTime} from "@/utils/format.js";

defineProps({
  rows: {type: Array, default: () => []},
  loading: {type: Boolean, default: false},
  total: {type: Number, default: 0},
  pageNum: {type: Number, default: 1},
  pageSize: {type: Number, default: 20},
  showRefresh: {type: Boolean, default: true}
});

const emit = defineEmits([
  "create",
  "refresh",
  "edit",
  "discount",
  "log",
  "page-change",
  "size-change",
  "changed"
]);

const {togglingId, onEnabledChange} = useEnabledToggle({
  updateApi: updateProviderStatus
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
