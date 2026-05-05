<template>
  <div class="kv-editor">
    <div v-for="(item, idx) in pairs" :key="idx" class="kv-row">
      <el-input
        v-model="item.key"
        placeholder="键"
        class="kv-key"
        @change="emitUpdate"
      />
      <el-input
        v-model="item.val"
        placeholder="值"
        class="kv-val"
        @change="emitUpdate"
      />
      <el-button
        link
        type="danger"
        :icon="Delete"
        @click="removeRow(idx)"
      />
    </div>
    <el-button size="small" :icon="Plus" @click="addRow">添加</el-button>
  </div>
</template>

<script setup>
import { reactive, watch } from "vue";
import { Delete, Plus } from "@element-plus/icons-vue";

const props = defineProps({
  /** v-model 绑定的 JSON 对象 */
  modelValue: { type: Object, default: () => ({}) },
  /** 值类型提示，number 时自动将值转为数字 */
  valueMode: { type: String, default: "string" } // "string" | "number"
});

const emit = defineEmits(["update:modelValue"]);

const pairs = reactive([]);

/** 从 Object → pairs 数组 */
function objectToPairs(obj) {
  pairs.length = 0;
  if (obj && typeof obj === "object") {
    for (const [k, v] of Object.entries(obj)) {
      pairs.push({ key: k, val: String(v) });
    }
  }
}

/** 从 pairs 数组 → Object 并 emit */
function emitUpdate() {
  const result = {};
  for (const p of pairs) {
    if (!p.key?.trim()) continue;
    let val = p.val;
    if (props.valueMode === "number" && val !== "" && !isNaN(Number(val))) {
      val = Number(val);
    }
    result[p.key.trim()] = val;
  }
  emit("update:modelValue", result);
}

function addRow() {
  pairs.push({ key: "", val: "" });
}

function removeRow(idx) {
  pairs.splice(idx, 1);
  emitUpdate();
}

/** 外部 modelValue 变化时同步到内部 pairs（仅 dialog 打开时触发） */
watch(
  () => props.modelValue,
  (val) => objectToPairs(val),
  { immediate: true, deep: false }
);
</script>

<style scoped>
.kv-editor {
  width: 100%;
}
.kv-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.kv-key {
  width: 120px;
  flex-shrink: 0;
}
.kv-val {
  flex: 1;
}
</style>
