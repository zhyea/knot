<template>
  <el-dialog v-model="visible" title="新建插件" width="480px" destroy-on-close>
    <el-form :model="form" label-width="100px">
      <el-form-item label="编码" required>
        <el-input v-model="form.code" />
      </el-form-item>
      <el-form-item label="名称" required>
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item label="版本">
        <el-input v-model="form.version" />
      </el-form-item>
      <el-form-item label="状态">
        <EnumControl v-model="form.status" enum-name="PluginInstanceStatusEnum" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import {computed, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import EnumControl from "../common/EnumControl.vue";
import {createPlugin} from "@/api/plugins";

const props = defineProps({
  modelValue: { type: Boolean, default: false }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value)
});

const saving = ref(false);
const form = reactive({ code: "", name: "", version: "0.0.1", status: 2 });

watch(
  () => props.modelValue,
  (value) => {
    if (value) resetForm();
  }
);

function resetForm() {
  form.code = "";
  form.name = "";
  form.version = "0.0.1";
  form.status = 2;
}

async function submit() {
  if (!form.code?.trim() || !form.name?.trim()) {
    ElMessage.warning("请填写编码与名称");
    return;
  }
  saving.value = true;
  try {
    await createPlugin({
      id: null,
      code: form.code.trim(),
      name: form.name.trim(),
      version: form.version,
      status: form.status
    });
    ElMessage.success("已创建");
    visible.value = false;
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>
