<template>
  <div>
    <el-table
        v-loading="loading"
        :data="rows"
        stripe
        border
        style="width: 100%"
        @selection-change="(selection) => emit('selection-change', selection)"
    >
      <el-table-column type="selection" width="48" fixed="left"/>
      <el-table-column prop="sourceModelName" label="外部模型" min-width="180" show-overflow-tooltip/>
      <el-table-column prop="sourceProviderName" label="厂商" min-width="120" show-overflow-tooltip/>
      <el-table-column prop="sourceLlmType" label="类型" min-width="110" show-overflow-tooltip/>
      <el-table-column prop="sourceContextLength" label="上下文" min-width="100" show-overflow-tooltip/>
      <el-table-column label="更新时间" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ formatDateTime(row.sourceLastUpdateTime || row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
              :actions="[
              { key: 'view', label: '查看', icon: View },
              { key: 'create', label: '一键创建统一模型', icon: Plus, hidden: row.logicalModelId },
              { key: 'delete', label: '删除', icon: Delete, type: 'danger', confirm: '确认物理删除该外部模型？' }
            ]"
              @action="(action) => emit('action', action, row)"
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
import {Delete, Plus, View} from "@element-plus/icons-vue";
import RowActions from "../common/RowActions.vue";

defineProps({
  rows: {type: Array, default: () => []},
  loading: {type: Boolean, default: false},
  total: {type: Number, default: 0},
  pageNum: {type: Number, default: 1},
  pageSize: {type: Number, default: 20}
});

const emit = defineEmits(["selection-change", "action", "page-change", "size-change"]);

function formatDateTime(value) {
  if (!value) return "—";
  return String(value).replace("T", " ").slice(0, 19);
}
</script>
