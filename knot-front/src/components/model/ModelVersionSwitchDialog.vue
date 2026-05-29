<template>
  <el-dialog
    :model-value="modelValue"
    title="切换活跃版本"
    width="400px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-width="100px">
      <el-form-item label="目标版本">
        <el-input v-model="targetVersion" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { switchModelVersion } from "../../api/models";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  model: { type: Object, default: null }
});

const emit = defineEmits(["update:modelValue", "switched"]);

const loading = ref(false);
const targetVersion = ref("");

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      targetVersion.value = props.model?.version || "";
    }
  }
);

async function submit() {
  if (!targetVersion.value?.trim()) {
    ElMessage.warning("请填写版本号");
    return;
  }
  if (!props.model?.id) return;
  loading.value = true;
  try {
    await switchModelVersion(props.model.id, { targetVersion: targetVersion.value.trim() });
    ElMessage.success("已切换");
    emit("update:modelValue", false);
    emit("switched");
  } finally {
    loading.value = false;
  }
}
</script>
