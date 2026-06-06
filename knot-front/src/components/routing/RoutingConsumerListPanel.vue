<template>
  <div>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="consumerCode" label="消费者编码" min-width="160" show-overflow-tooltip />
      <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="userName" label="用户" min-width="120" show-overflow-tooltip />
      <el-table-column label="API Key" min-width="240">
        <template #default="{ row }">
          <div class="secret-cell">
            <el-input :model-value="row.secretKey" readonly type="password" show-password size="small" />
            <el-button size="small" :icon="CopyDocument" @click="emit('copy-secret', row.secretKey)" />
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="ruleCount" label="规则数" width="90" align="center" />
      <el-table-column label="消耗明细" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.returnUsageDetail ? 'success' : 'info'">
            {{ row.returnUsageDetail ? "返回" : "不返回" }}
          </el-tag>
        </template>
      </el-table-column>
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
      <el-table-column label="操作" width="110" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'rotate', label: '重置 Key', icon: Key, type: 'warning', confirm: '重置后旧 API Key 将不可用，确认继续？' }
            ]"
            @action="(action) => emit('action', action, row)"
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
import { CopyDocument, Edit, Key } from "@element-plus/icons-vue";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";

defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  togglingId: { type: [Number, String], default: null }
});

const emit = defineEmits([
  "create",
  "refresh",
  "action",
  "enabled-change",
  "copy-secret",
  "page-change",
  "size-change"
]);
</script>

<style scoped>
.secret-cell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}
</style>
