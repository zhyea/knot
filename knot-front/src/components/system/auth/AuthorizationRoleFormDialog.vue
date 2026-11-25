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

<script setup lang="ts">
import type {Dict} from "@/types";
import {type PropType,  computed, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import {
  createAuthorizationRole,
  getAuthorizationRole,
  updateAuthorizationRole
} from "@/api/authorizations";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 编辑态只接主键：打开时按 id 拉详情，不复用列表行数据 */
  roleId: { type: Number as PropType<number | null>, default: null }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
const isEdit = computed(() => props.roleId != null);
const form = reactive({
  code: "",
  name: "",
  description: ""
});

function resetForm(detail: Dict | null): void {
  form.code = detail?.code || "";
  form.name = detail?.name || "";
  form.description = detail?.description || "";
}

watch(
  () => [props.modelValue, props.roleId] as const,
  async ([visible]) => {
    if (!visible) return;
    if (props.roleId == null) {
      resetForm(null);
      return;
    }
    let detail: Dict;
    try {
      const loaded = await getAuthorizationRole(props.roleId);
      if (!loaded) throw new Error("角色详情为空");
      detail = loaded as Dict;
    } catch {
      ElMessage.error("角色详情加载失败");
      emit("update:modelValue", false);
      return;
    }
    resetForm(detail);
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
      ? await updateAuthorizationRole(props.roleId!, payload)
      : await createAuthorizationRole(payload);
    ElMessage.success("已保存");
    emit("update:modelValue", false);
    emit("saved", saved);
  } finally {
    saving.value = false;
  }
}
</script>
