<template>
  <div>
    <el-table v-loading="loading" :data="rows" stripe border size="small" style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center"/>
      <el-table-column label="供应商" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.providerName || (row.providerCode ? `#${row.providerCode}` : "全局") }}
        </template>
      </el-table-column>
      <el-table-column label="统一模型" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.logicalModelName || (row.logicalModelCode ? `#${row.logicalModelCode}` : "默认") }}
        </template>
      </el-table-column>
      <el-table-column label="版本" width="120" align="center" show-overflow-tooltip>
        <template #default="{ row }">{{ row.versionCode || "-" }}</template>
      </el-table-column>
      <el-table-column label="计费模式" width="120">
        <template #default="{ row }">{{ billingModeLabel(row.billingMode) }}</template>
      </el-table-column>
      <el-table-column label="进阶方案" width="110">
        <template #default="{ row }">{{ pricingPlanLabel(row.pricingPlan) }}</template>
      </el-table-column>
      <el-table-column label="阶梯" width="130" align="center">
        <template #default="{ row }">
          <el-button
            v-if="tierCount(row)"
            link
            type="primary"
            @click="emit('tier-detail', row)"
          >
            {{ tierCount(row) }} 档 · 明细
          </el-button>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="currency" label="币种" width="80" align="center"/>
      <el-table-column prop="unit" label="单位" width="110"/>
      <el-table-column label="启用" width="88" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled !== false"
            :loading="togglingId === row.id"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
            @change="(value) => emit('enabled-change', row, value)"
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
            @action="(action) => emit(action, row)"
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
import {Delete, Document, Edit} from "@element-plus/icons-vue";
import type {Row} from "@/types";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {parseTierRows} from "@/utils/billingTier";
import {parseJsonObject} from "@/utils/format";

defineProps({
  rows: {type: Array, default: (): Row[] => []},
  loading: {type: Boolean, default: false},
  total: {type: Number, default: 0},
  pageNum: {type: Number, default: 1},
  pageSize: {type: Number, default: 20},
  togglingId: {type: [Number, String] as PropType<number | string | null>, default: null},
  showRefresh: {type: Boolean, default: true}
});

const emit = defineEmits([
  "create",
  "edit",
  "log",
  "tier-detail",
  "delete",
  "refresh",
  "enabled-change",
  "page-change",
  "size-change"
]);
const {labelOf: enumLabelOf} = useEnumOptions();

function billingModeLabel(code: unknown): string {
  return enumLabelOf("BillingModeEnum", code);
}

function pricingPlanLabel(code: unknown): string {
  return enumLabelOf("PricingPlanEnum", code, "-");
}

/** 当前版本的阶梯档数；0 表示非阶梯或无档位（列表据此隐藏明细入口） */
function tierCount(row: Row): number {
  return parseTierRows(parseJsonObject(row.configJson).tier).length;
}
</script>
