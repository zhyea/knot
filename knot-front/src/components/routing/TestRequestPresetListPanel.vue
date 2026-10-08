<template>
  <div>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="code" label="预设编码" min-width="160" show-overflow-tooltip />
      <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
      <el-table-column label="协议" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ protocolLabel(row.protocolCode) }}
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="启用" width="88" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            :loading="togglingId === row.id"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
            @change="(value) => emit('enabled-change', row, value)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'view', label: '查看', icon: View },
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'delete', label: '删除', icon: Delete, type: 'danger', confirm: '删除后无法恢复，确认继续？' }
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
      :show-refresh="showRefresh"
      @refresh="emit('refresh')"
      @page-change="(page) => emit('page-change', page)"
      @size-change="(size) => emit('size-change', size)"
    />
  </div>
</template>

<script setup lang="ts">
import {type PropType} from "vue";
import {Edit, Delete, View} from "@element-plus/icons-vue";
import type {Row} from "@/types";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";
import {useEnumOptions} from "@/composables/useEnumOptions";

defineProps({
  rows: {type: Array as PropType<Row[]>, default: (): Row[] => []},
  loading: {type: Boolean, default: false},
  total: {type: Number, default: 0},
  pageNum: {type: Number, default: 1},
  pageSize: {type: Number, default: 20},
  togglingId: {type: [Number, String] as PropType<number | string | null>, default: null},
  showRefresh: {type: Boolean, default: true}
});

const emit = defineEmits([
  "create",
  "refresh",
  "action",
  "enabled-change",
  "page-change",
  "size-change"
]);

const {labelOf} = useEnumOptions();

function protocolLabel(code: unknown): string {
  return labelOf("ModelApiProtocolEnum", code, String(code ?? "-"));
}
</script>
