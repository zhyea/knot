<template>
  <div>
    <el-table
      v-loading="loading"
      :data="rows"
      :row-class-name="rowClassName"
      stripe
      border
      style="width: 100%"
    >
      <el-table-column prop="id" label="ID" min-width="60"/>
      <el-table-column prop="modelCode" label="模型编码" min-width="240" show-overflow-tooltip/>
      <el-table-column label="模型名称" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">{{ displayText(row.modelName) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="120" show-overflow-tooltip>
        <template #default="{ row }">
          <!-- 守卫必须与 modelTypeLabel 的空值口径（唯一出口）同步：写成 '-' 会把占位符渲染成标签 -->
          <el-tag v-if="modelTypeLabel(row.modelType) !== EMPTY_PLACEHOLDER" effect="plain">
            {{ modelTypeLabel(row.modelType) }}
          </el-tag>
          <span v-else>{{ EMPTY_PLACEHOLDER }}</span>
        </template>
      </el-table-column>
      <el-table-column label="模型族" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ familyLabel(row.modelFamily) }}</template>
      </el-table-column>
      <el-table-column label="启用" width="88" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="isRowEnabled(row)"
            :loading="togglingId === row.id"
            :disabled="isDeletedRow(row)"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
            @change="(value) => handleEnabledChange(row, value)"
          />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="180" align="center" show-overflow-tooltip>
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="180" align="center" show-overflow-tooltip>
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="190" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="rowActions(row)"
            @action="(action) => emit('action', action, row)"
          />
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无统一模型"/>
      </template>
    </el-table>

    <ListPagination
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      :page-sizes="pageSizes"
      :show-refresh="showRefresh"
      @refresh="emit('refresh')"
      @page-change="(page) => emit('page-change', page)"
      @size-change="(size) => emit('size-change', size)"
    />
  </div>
</template>

<script setup lang="ts">
import {isRowEnabled} from "@/utils/enabledFlag";
import {displayText, formatDateTime, EMPTY_PLACEHOLDER} from "@/utils/format";
import {onMounted, type PropType} from "vue";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";
import {updateLogicalModelStatus} from "@/api/logicalModels";
import {useEnabledToggle} from "@/composables/useEnabledToggle";
import {useEnums} from "@/composables/useEnums";
import type {Row} from "@/types";
import {useLogicalModelPresentation} from "@/composables/useLogicalModelPresentation";
import {logicalModelRowActions} from "./logicalModelRowActions";
import {deletedRowClassName, isDeletedRow} from "@/utils/rowState";

defineProps({
  rows: {type: Array as PropType<Row[]>, default: (): Row[] => []},
  loading: {type: Boolean, default: false},
  total: {type: Number, default: 0},
  pageNum: {type: Number, default: 1},
  pageSize: {type: Number, default: 20},
  pageSizes: {type: Array as PropType<number[]>, default: (): number[] => [10, 20, 50]},
  showRefresh: {type: Boolean, default: true}
});

const emit = defineEmits(["action", "refresh", "page-change", "size-change", "changed"]);

const {togglingId, handleEnabledChange} = useEnabledToggle({
  updateApi: updateLogicalModelStatus,
  onChanged: () => emit("changed")
});

const {options: familyOptions, loadOptions: loadFamilyOptions} = useEnums("model_family");
const {modelTypeLabel, familyLabel} = useLogicalModelPresentation({familyOptions: () => familyOptions.value});
/** 与抽屉下拉同一口径：强制回源，避免列表标签停在旧值而下拉已是新值 */
onMounted(() => loadFamilyOptions(true));

/** 已删除行：浅红底标识（排序已由后端 is_deleted asc 放到末尾） */
const rowClassName = deletedRowClassName;

const rowActions = logicalModelRowActions;
</script>
