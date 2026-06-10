<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑角色' : '新建角色'"
    width="520px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="88px">
      <el-form-item label="角色编码" required>
        <el-input v-model="form.code" maxlength="64" placeholder="例如: SYSTEM_ADMIN" />
      </el-form-item>
      <el-form-item label="角色名称" required>
        <el-input v-model="form.name" maxlength="64" placeholder="请输入角色名称" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="255" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import {
  createAuthorizationRole,
  updateAuthorizationRole
} from "../../../api/authorizations";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  role: { type: Object, default: null }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
const isEdit = computed(() => !!props.role?.id);
const form = reactive({
  code: "",
  name: "",
  description: ""
});

function resetForm() {
  form.code = props.role?.code || "";
  form.name = props.role?.name || "";
  form.description = props.role?.description || "";
}

watch(
  () => [props.modelValue, props.role],
  ([visible]) => {
    if (visible) {
      resetForm();
    }
  },
  { immediate: true }
);

async function submit() {
  if (!form.code.trim()) {
    ElMessage.warning("请填写角色编码");
    return;
  }
  if (!form.name.trim()) {
    ElMessage.warning("请填写角色名称");
    return;
  }
  saving.value = true;
  try {
    const payload = {
      code: form.code.trim(),
      name: form.name.trim(),
      description: form.description?.trim() || null
    };
    const saved = isEdit.value
      ? await updateAuthorizationRole(props.role.id, payload)
      : await createAuthorizationRole(payload);
    ElMessage.success("保存成功");
    emit("update:modelValue", false);
    emit("saved", saved);
  } finally {
    saving.value = false;
  }
}
</script>
