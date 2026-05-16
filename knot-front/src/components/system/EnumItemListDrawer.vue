<template>
  <el-drawer
      :model-value="modelValue"
      :title="`枚举值 — ${category || ''}`"
      size="50%"
      destroy-on-close
      @update:model-value="emit('update:modelValue', $event)"
      @closed="onClosed"
  >
    <div class="drawer-toolbar">
      <el-button type="primary" size="small" @click="emit('create')">新增枚举值</el-button>
      <el-button size="small" @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="items" stripe border size="small">
      <el-table-column prop="id" label="ID" width="70" align="center"/>
      <el-table-column prop="itemCode" label="编码" width="140" show-overflow-tooltip/>
      <el-table-column prop="itemLabel" label="显示名" min-width="120" show-overflow-tooltip/>
      <el-table-column prop="sortOrder" label="排序" width="72" align="center"/>
      <el-table-column label="启用" width="88" align="center">
        <template #default="{ row }">
          <el-switch
              :model-value="row.isEnabled !== false"
              :loading="togglingId === row.id"
              :disabled="row.isSystem"
              inline-prompt
              active-text="启用"
              inactive-text="禁用"
              @change="(val) => onEnabledChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="row.isSystem" @click="emit('edit', row)">编辑</el-button>
          <el-button link type="danger" :disabled="row.isSystem" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-drawer>
</template>

<script setup>
import {ref, watch} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import {listEnumItemsByCategory, deleteEnumConfig, updateEnumConfig} from "../../api/enums";
import {clearEnumCache} from "../../composables/useEnums";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  category: {type: String, default: ""}
});

const emit = defineEmits(["update:modelValue", "create", "edit", "changed"]);

const loading = ref(false);
const items = ref([]);
const togglingId = ref(null);

async function load() {
  if (!props.category) return;
  loading.value = true;
  try {
    const data = await listEnumItemsByCategory(props.category);
    items.value = Array.isArray(data) ? data : [];
  } finally {
    loading.value = false;
  }
}

function onClosed() {
  items.value = [];
}

watch(
    () => [props.modelValue, props.category],
    ([visible, category]) => {
      if (visible && category) load();
    }
);

async function onEnabledChange(row, enabled) {
  if (row.isSystem) return;
  const prev = row.isEnabled !== false;
  if (enabled === prev) return;

  togglingId.value = row.id;
  row.isEnabled = enabled;
  try {
    await updateEnumConfig(row.id, {
      category: row.category,
      itemCode: row.itemCode,
      itemLabel: row.itemLabel,
      sortOrder: row.sortOrder ?? 0,
      isEnabled: enabled,
      remark: row.remark ?? ""
    });
    ElMessage.success(enabled ? "已启用" : "已禁用");
    clearEnumCache();
    emit("changed");
  } catch {
    row.isEnabled = prev;
  } finally {
    togglingId.value = null;
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(
      `确认删除枚举「${row.itemLabel}」(${row.category}/${row.itemCode})？`,
      "删除确认",
      {type: "warning"}
  );
  await deleteEnumConfig(row.id);
  ElMessage.success("已删除");
  clearEnumCache();
  await load();
  emit("changed");
}

defineExpose({reload: load});
</script>

<style scoped>
.drawer-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
