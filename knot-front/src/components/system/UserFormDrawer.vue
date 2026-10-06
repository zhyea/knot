<template>
  <el-drawer
    :model-value="modelValue"
    :title="isEdit ? '编辑用户' : '新建用户'"
    size="40%"
    class="drawer-with-scrollbar"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-scrollbar max-height="calc(100vh - 140px)">
      <el-form :model="form" label-width="90px">
        <div class="slot-body">
          <el-form-item label="用户名">
            <el-input v-model="form.username" :disabled="isEdit" placeholder="请输入用户名"/>
          </el-form-item>
          <el-form-item label="姓名">
            <el-input v-model="form.realName" placeholder="请输入姓名"/>
          </el-form-item>
          <el-form-item label="所属部门">
            <RemoteEntitySelect
              v-model="form.deptId"
              :load-function="loadDepartmentOptions"
              value-key="value"
              :code-only="false"
              label-key="label"
              placeholder="请选择部门"
              clearable
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="绑定角色">
            <RemoteEntitySelect
              v-model="form.roleIds"
              :load-function="loadRoleOptions"
              value-key="value"
              :code-only="false"
              label-key="label"
              multiple
              placeholder="请选择角色"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item v-if="!isEdit" label="密码" required>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请设置初始密码"
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
        </div>
      </el-form>
    </el-scrollbar>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import {type PropType, computed, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import {createUser, updateUser} from "@/api/users";
import {listDepartmentOptions, listRoleOptions} from "@/api/options";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import {toOptionsLoader} from "@/utils/options";
import type {Dict} from "@/types";
import {useMissingOptionGuard} from "@/composables/useMissingOptionGuard";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  user: {type: Object as PropType<Dict | null>, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
interface UserFormState {
  id: number | string | null;
  username: string;
  realName: string;
  password: string;
  deptId: number | string | null;
  roleIds: number[];
  status: number;
}

const form = reactive<UserFormState>({
  id: null,
  username: "",
  realName: "",
  password: "",
  deptId: null,
  roleIds: [],
  status: 1
});

const isEdit = computed(() => props.user != null);

function resetForm() {
  if (props.user) {
    form.id = props.user.id;
    form.username = props.user.username;
    form.realName = props.user.realName || "";
    form.password = "";
    form.deptId = props.user.deptId ?? null;
    form.roleIds = Array.isArray(props.user.roleIds) ? [...props.user.roleIds] : [];
    form.status = props.user.status ?? 1;
    return;
  }
  form.id = null;
  form.username = "";
  form.realName = "";
  form.password = "";
  form.deptId = null;
  form.roleIds = [];
  form.status = 1;
}

watch(
  () => [props.modelValue, props.user],
  ([visible]) => {
    if (visible) {
      resetForm();
    }
  },
  {immediate: true}
);

const loadDepartmentOptions = toOptionsLoader(listDepartmentOptions);

/** 角色下拉：value=id，label 由后端给出（name）；编辑态回显由组件自动带 values 交后端。 */
const loadRoleOptions = toOptionsLoader(listRoleOptions);

// 下拉缺失值守卫：已选项被删除/无权限时阻止提交（options 契约第 10 条）
const missing = useMissingOptionGuard();

async function submit() {
  if (missing.hasMissing.value) {
    ElMessage.warning(missing.hint.value);
    return;
  }
  if (!form.username?.trim()) {
    ElMessage.warning("请填写用户名");
    return;
  }
  if (!isEdit.value && !form.password?.trim()) {
    ElMessage.warning("请设置初始密码");
    return;
  }
  saving.value = true;
  try {
    const payload: Dict = {
      id: form.id,
      username: form.username.trim(),
      realName: form.realName?.trim() || form.username.trim(),
      deptId: form.deptId,
      roleIds: Array.isArray(form.roleIds) ? [...form.roleIds] : [],
      status: form.status
    };
    if (!isEdit.value) {
      payload.password = form.password.trim();
      await createUser(payload);
      ElMessage.success("已创建");
    } else {
      await updateUser(form.id!, payload);
      ElMessage.success("已保存");
    }
    emit("update:modelValue", false);
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>
