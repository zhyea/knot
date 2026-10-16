<template>
  <div>
    <el-table
      v-loading="loading"
      :data="rows"
      stripe
      border
      style="width: 100%"
      :row-class-name="rowClassName"
    >
      <el-table-column prop="id" label="ID" min-width="5%" align="center" header-align="center" />
      <el-table-column prop="code" label="编码" min-width="12%" />
      <el-table-column label="名称" min-width="18%">
        <template #default="{ row }">
          <span>{{ row.name }}</span>
          <el-tag
            v-if="row.orphaned === 1"
            type="info"
            effect="plain"
            class="plugin-orphan-tag"
          >已删除(代码移除)</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="类型" min-width="10%">
        <template #default="{ row }">{{ pluginTypeLabel(row.pluginType) }}</template>
      </el-table-column>
      <el-table-column prop="version" label="版本" min-width="8%" />
      <el-table-column label="状态" min-width="10%" align="center" header-align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === EnabledStatus.ENABLED"
            :loading="statusUpdatingId === row.id"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
            @change="(value) => emit('status-change', row, value)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'log', label: '操作日志', icon: Document },
              { key: 'delete', label: '删除', icon: Delete, type: 'danger', confirm: '确认逻辑删除该插件？删除后将从列表隐藏（孤儿插件可彻底移除）' }
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
import { EnabledStatus } from "@/constants/status";
import type {PropType} from "vue";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";
import { Document, Delete } from "@element-plus/icons-vue";
import { useEnumOptions } from "@/composables/useEnumOptions";
import type {Row} from "@/types";

const { labelOf } = useEnumOptions();

defineProps({
  rows: { type: Array as PropType<Row[]>, default: (): Row[] => [] },
  loading: { type: Boolean, default: false },
  statusUpdatingId: { type: Number as PropType<number | null>, default: null },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  showRefresh: { type: Boolean, default: true }
});

const emit = defineEmits(["refresh", "status-change", "action", "page-change", "size-change"]);

function pluginTypeLabel(code: unknown): string {
  return labelOf("PluginTypeEnum", code);
}

function rowClassName({ row }: { row: Row }): string {
  return row.orphaned === 1 ? "plugin-row--orphaned" : "";
}
</script>

<style scoped>
.plugin-orphan-tag {
  margin-left: 8px;
  vertical-align: middle;
}

:deep(.plugin-row--orphaned) {
  background-color: #fef0f0;
}
</style>
