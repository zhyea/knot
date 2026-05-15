<template>
  <el-drawer
    :model-value="modelValue"
    :title="isEdit ? '编辑用户' : '新建用户'"
    size="480px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="90px">
      <el-form-item label="用户名">
        <el-input v-model="form.username" :disabled="isEdit" placeholder="请输入用户名" />
      </el-form-item>
      <el-form-item label="姓名">
        <el-input v-model="form.realName" placeholder="请输入姓名" />
      </el-form-item>
      <el-form-item v-if="isEdit" label="密码">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="留空则不修改密码"
          show-password
        />
      </el-form-item>
      <el-form-item v-else label="密码" required>
        <el-input
          v-model="form.password"
          type="password"
          placeholder="请设置密码"
          show-password
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-switch
          v-model="form.status"
          :active-value="1"
          :inactive-value="0"
          active-text="启用"
          inactive-text="禁用"
          inline-prompt
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { createUser, updateUser } from "../../api/users";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 传入用户对象表示编辑，null 表示新建 */
  user: { type: Object, default: null }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
const form = reactive({
  id: null,
  username: "",
  realName: "",
  password: "",
  status: 1
});

const isEdit = computed(() => props.user != null);

function resetForm() {
  if (props.user) {
    form.id = props.user.id;
    form.username = props.user.username;
    form.realName = props.user.realName;
    form.password = "";
    form.status = props.user.status;
  } else {
    form.id = null;
    form.username = "";
    form.realName = "";
    form.password = "";
    form.status = 1;
  }
}

watch(
  () => [props.modelValue, props.user],
  ([visible]) => {
    if (visible) resetForm();
  },
  { immediate: true }
);

async function submit() {
  if (!form.username?.trim()) {
    ElMessage.warning("请填写用户名");
    return;
  }
  if (!isEdit.value && !form.password?.trim()) {
    ElMessage.warning("请设置密码");
    return;
  }
  saving.value = true;
  try {
    const payload = {
      id: form.id,
      username: form.username.trim(),
      realName: form.realName?.trim() || form.username.trim(),
      status: form.status
    };
    if (form.password?.trim()) {
      payload.password = form.password.trim();
    }
    if (isEdit.value) {
      await updateUser(form.id, payload);
      ElMessage.success("已保存");
    } else {
      await createUser(payload);
      ElMessage.success("已创建");
    }
    emit("update:modelValue", false);
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>
