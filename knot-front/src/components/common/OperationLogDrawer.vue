<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    :size="drawerSize"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-table v-loading="loading" :data="logs" stripe border size="small" max-height="calc(100vh - 140px)">
      <el-table-column label="时间" width="180" align="center" header-align="center">
        <template #default="{ row }">
          {{ formatLogTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="operation" label="操作" width="80" align="center" header-align="center" />
      <el-table-column v-if="showEntityName" prop="entityName" label="对象" min-width="120" show-overflow-tooltip />
      <el-table-column prop="status" label="结果" width="80" align="center" header-align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
            {{ row.status === "SUCCESS" ? "成功" : "失败" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="showDescription" prop="description" label="说明" min-width="120" show-overflow-tooltip />
      <el-table-column v-if="showOldNew" prop="oldValue" label="旧值" min-width="160" show-overflow-tooltip />
      <el-table-column v-if="showOldNew" prop="newValue" label="新值" min-width="160" show-overflow-tooltip />
      <el-table-column prop="errorMsg" label="错误" min-width="120" show-overflow-tooltip />
    </el-table>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from "vue";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 抽屉标题 */
  title: { type: String, default: "操作日志" },
  /** 抽屉宽度，如 `60%`、`640px` */
  drawerSize: { type: String, default: "60%" },
  /** 打开且需加载时调用，应返回日志数组（或 Promise） */
  loadLogs: { type: Function, required: true },
  /** 是否展示「说明」列（默认不展示） */
  showDescription: { type: Boolean, default: false },
  /** 是否展示「对象」(entityName) 列 */
  showEntityName: { type: Boolean, default: true },
  /** 是否展示「旧值 / 新值」列 */
  showOldNew: { type: Boolean, default: true },
});

const emit = defineEmits(["update:modelValue"]);

const loading = ref(false);
const logs = ref([]);

async function load() {
  loading.value = true;
  try {
    const data = await props.loadLogs();
    logs.value = Array.isArray(data) ? data : [];
  } catch {
    logs.value = [];
  } finally {
    loading.value = false;
  }
}

watch(
  () => [props.modelValue],
  ([open]) => {
    if (open) {
      load();
    }
  },
  { flush: "post" }
);

defineExpose({ reload: load });

/** 展示为 2026-05-15 22:52:52 */
function formatLogTime(val) {
  if (val == null || val === "") {
    return "-";
  }
  const d = new Date(val);
  if (Number.isNaN(d.getTime())) {
    return typeof val === "string" ? val : "-";
  }
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}
</script>
