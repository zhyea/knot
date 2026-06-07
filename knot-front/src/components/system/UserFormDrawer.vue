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
              :load-function="listDepartments"
              :label-function="departmentLabel"
              :selected-options="selectedDepartmentOptions"
              placeholder="请选择部门"
              clearable
              style="width: 100%"
          />
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
      </div>
    </el-form>
    </el-scrollbar>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import {computed, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import {createUser, updateUser} from "../../api/users";
import {listDepartments} from "../../api/departments";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import { normalizeOptionList, resolveSelectedOption } from "../../utils/options";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  /** 传入用户对象表示编辑，null 表示新建 */
  user: {type: Object, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
const departmentOptions = ref([]);
const form = reactive({
  id: null,
  username: "",
  realName: "",
  password: "",
  deptId: null,
  status: 1
});

const isEdit = computed(() => props.user != null);

function resetForm() {
  if (props.user) {
    form.id = props.user.id;
    form.username = props.user.username;
    form.realName = props.user.realName;
    form.password = "";
    form.deptId = props.user.deptId ?? null;
    form.status = props.user.status;
  } else {
    form.id = null;
    form.username = "";
    form.realName = "";
    form.password = "";
    form.deptId = null;
    form.status = 1;
  }
}

watch(
    () => [props.modelValue, props.user],
    ([visible]) => {
      if (visible) {
        resetForm();
        loadDepartments();
      }
    },
    {immediate: true}
);

const selectedDepartmentOptions = computed(() =>
  resolveSelectedOption(form.deptId, departmentOptions.value, {
    id: form.deptId,
    deptName: props.user?.deptName
  })
);

function departmentLabel(department) {
  return department.deptCode ? `${department.deptName}（${department.deptCode}）` : (department.deptName || `#${department.id}`);
}

async function loadDepartments() {
  const data = await listDepartments({ pageNum: 1, pageSize: 100 });
  departmentOptions.value = normalizeOptionList(data);
}

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
      deptId: form.deptId,
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
