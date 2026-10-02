<template>
  <div>
    <el-table v-loading="loading" :data="rows" stripe border size="small" style="width: 100%">
      <el-table-column prop="code" label="规则编码" min-width="160" show-overflow-tooltip/>
      <el-table-column label="模型族" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.modelFamilyName || (row.modelFamilyCode ? `#${row.modelFamilyCode}` : "默认") }}
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
              { key: 'preview', label: '试算', icon: DataAnalysis },
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
import {DataAnalysis, Delete, Document, Edit} from "@element-plus/icons-vue";
import type {Row} from "@/types";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";
import {useEnumOptions} from "@/composables/useEnumOptions";

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
  "preview",
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
</script>
