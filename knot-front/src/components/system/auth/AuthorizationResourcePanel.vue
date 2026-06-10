<template>
  <section class="auth-panel">
    <div class="auth-panel__header">
      <div class="auth-panel__title">{{ title }}</div>
      <div class="auth-panel__actions">
        <el-button @click="emit('reset-filters')">重置</el-button>
        <el-button type="primary" @click="emit('query')">查询</el-button>
        <el-button type="primary" @click="emit('create')">{{ createText }}</el-button>
      </div>
    </div>

    <div v-if="filters.length > 0" class="auth-panel__filters">
      <div
        v-for="filter in filters"
        :key="filter.key"
        class="list-filter-item"
        :style="filterItemStyle(filter)"
      >
        <span class="list-filter-label">{{ filter.label }}</span>
        <el-input
          v-if="!filter.type || filter.type === 'input'"
          :model-value="filterValues[filter.key]"
          clearable
          :placeholder="filter.placeholder"
          class="list-filter-control--wide auth-panel__filter-control"
          :style="filterControlStyle(filter)"
          @update:model-value="(value) => emit('filter-change', filter.key, value)"
          @keyup.enter="emit('query')"
        />
        <el-select
          v-else
          :model-value="filterValues[filter.key]"
          clearable
          filterable
          :placeholder="filter.placeholder"
          class="list-filter-control--wide auth-panel__filter-control"
          :style="filterControlStyle(filter)"
          @update:model-value="(value) => emit('filter-change', filter.key, value)"
        >
          <el-option
            v-for="option in filter.options || []"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>
    </div>

    <el-table v-loading="loading" :data="rows" stripe border size="small">
      <el-table-column
        v-for="column in columns"
        :key="column.prop || column.label"
        :prop="column.prop"
        :label="column.label"
        :min-width="column.minWidth"
        :width="column.width"
        :align="column.align"
        :show-overflow-tooltip="column.showOverflowTooltip !== false"
      >
        <template v-if="column.slot" #default="{ row }">
          <slot :name="column.slot" :row="row" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="buildActions(row)"
            @action="(action) => emit('action', action, row)"
          />
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup>
import { Delete, Edit } from "@element-plus/icons-vue";
import RowActions from "../../common/RowActions.vue";

const props = defineProps({
  title: { type: String, required: true },
  createText: { type: String, required: true },
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  columns: { type: Array, default: () => [] },
  filters: { type: Array, default: () => [] },
  filterValues: { type: Object, default: () => ({}) },
  deleteName: { type: String, default: "当前记录" },
  beforeDelete: { type: Function, default: null }
});

const emit = defineEmits(["create", "action", "filter-change", "query", "reset-filters"]);

function filterItemStyle(filter) {
  return filter?.width ? { flex: `0 0 ${filter.width}`, width: filter.width } : undefined;
}

function filterControlStyle(filter) {
  return filter?.width ? { width: "100%" } : undefined;
}

function deleteConfirm(row) {
  const name = row?.name
    || row?.moduleName
    || row?.menuName
    || row?.permissionName
    || row?.pathPattern
    || props.deleteName;
  return `确认删除 ${name} ?`;
}

function buildActions(row) {
  const deleteAction = {
    key: "delete",
    label: "删除",
    icon: Delete,
    type: "danger",
    confirm: deleteConfirm(row)
  };
  if (typeof props.beforeDelete === "function") {
    Object.assign(deleteAction, props.beforeDelete(row) || {});
  }
  return [
    { key: "edit", label: "编辑", icon: Edit },
    deleteAction
  ];
}
</script>

<style scoped>
.auth-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.auth-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.auth-panel__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.auth-panel__title {
  font-size: 14px;
  color: #303133;
  font-weight: 600;
}

.auth-panel__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
}

.auth-panel__filter-control {
  min-width: 0;
}
</style>
