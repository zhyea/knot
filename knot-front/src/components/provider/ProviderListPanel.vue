<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="emit('create')">新建供应商</el-button>
      <el-button @click="emit('refresh')">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" min-width="5%" align="center" header-align="center" />
      <el-table-column prop="name" label="名称" min-width="15%" />
      <el-table-column prop="type" label="类型" min-width="10%" />
      <el-table-column prop="baseUrl" label="Base URL" min-width="28%" show-overflow-tooltip />
      <el-table-column label="启用" width="88" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled !== false"
            :loading="togglingId === row.id"
            inline-prompt
            active-text="启"
            inactive-text="停"
            @change="(val) => handleEnabledChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="18%" align="center" header-align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="emit('edit', row)">编辑</el-button>
          <el-button link type="primary" @click="emit('discount', row)">折扣策略</el-button>
          <el-button link type="primary" @click="emit('log', row)">日志</el-button>
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
import { updateProvider } from "../../api/providers";
import { useEnabledToggle } from "../../composables/useEnabledToggle";

defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 }
});

const emit = defineEmits(["create", "refresh", "edit", "discount", "log", "page-change", "size-change", "changed"]);

const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateProvider,
  buildPayload: (row, enabled) => ({
    name: row.name,
    type: row.type,
    baseUrl: row.baseUrl,
    enabled,
    rateLimitPolicy: row.rateLimitPolicy ?? null,
    quotaPolicy: row.quotaPolicy ?? null
  })
});

async function handleEnabledChange(row, enabled) {
  await onEnabledChange(row, enabled);
  emit("changed");
}
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
</style>
