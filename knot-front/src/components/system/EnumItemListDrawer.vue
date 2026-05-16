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
      <el-table-column label="启用" width="72" align="center">
        <template #default="{ row }">
          <StatusTag :active="row.isEnabled"/>
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
import StatusTag from "../common/StatusTag.vue";
import {listEnumItemsByCategory, deleteEnumConfig} from "../../api/enums";
import {clearEnumCache} from "../../composables/useEnums";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  category: {type: String, default: ""}
});

const emit = defineEmits(["update:modelValue", "create", "edit", "changed"]);

const loading = ref(false);
const items = ref([]);

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
