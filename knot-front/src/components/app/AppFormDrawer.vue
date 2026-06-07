<template>
  <el-drawer
      :model-value="modelValue"
      :title="isEdit ? '编辑应用' : '新建应用'"
      size="45%"
      class="drawer-with-scrollbar"
      destroy-on-close
      @update:model-value="emit('update:modelValue', $event)"
      @closed="onClosed"
  >
    <el-scrollbar max-height="calc(100vh - 140px)">
    <el-form :model="form" label-width="100px">
      <div class="slot-body">
        <el-form-item label="App ID" required>
          <el-input v-model="form.appId" :disabled="isEdit"/>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="form.name"/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
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
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人">
              <RemoteEntitySelect
                  v-model="form.ownerUserId"
                  :load-function="listUsers"
                  :label-function="userLabel"
                  :selected-options="selectedOwnerOptions"
                  placeholder="请选择负责人"
                  clearable
                  style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="选填"/>
        </el-form-item>
      </div>

      <div class="space-line"/>

      <TrafficPolicySection
          class="slot-body"
          v-model:rate-limit="form.rateLimitPolicy"
          v-model:quota="form.quotaPolicy"
      />
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
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import TrafficPolicySection from "../common/TrafficPolicySection.vue";
import {
  emptyQuotaPolicy,
  emptyRateLimitPolicy,
  isEmptyQuotaPolicy,
  isEmptyRateLimitPolicy,
  normalizeQuotaPolicy,
  normalizeRateLimitPolicy
} from "../../utils/trafficPolicy";
import {createApp, updateApp} from "../../api/apps";
import {listDepartments} from "../../api/departments";
import {listUsers} from "../../api/users";
import { normalizeOptionList, resolveSelectedOption } from "../../utils/options";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  app: {type: Object, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
const departmentOptions = ref([]);
const userOptions = ref([]);

const form = reactive({
  id: null,
  appId: "",
  name: "",
  deptId: null,
  ownerUserId: null,
  remark: "",
  rateLimitPolicy: emptyRateLimitPolicy(),
  quotaPolicy: emptyQuotaPolicy()
});

const isEdit = computed(() => props.app != null);
const selectedDepartmentOptions = computed(() =>
  resolveSelectedOption(form.deptId, departmentOptions.value, {
    id: form.deptId,
    deptName: props.app?.deptName
  })
);
const selectedOwnerOptions = computed(() =>
  resolveSelectedOption(form.ownerUserId, userOptions.value, {
    id: form.ownerUserId,
    realName: props.app?.ownerName
  })
);

function departmentLabel(department) {
  return department.deptCode ? `${department.deptName}（${department.deptCode}）` : (department.deptName || `#${department.id}`);
}

function userLabel(user) {
  const name = user.realName?.trim() || user.username;
  return name === user.username ? name : `${name}（${user.username}）`;
}

async function loadDepartments() {
  const data = await listDepartments({pageNum: 1, pageSize: 20});
  departmentOptions.value = normalizeOptionList(data);
}

async function loadUsers() {
  const data = await listUsers({pageNum: 1, pageSize: 10});
  userOptions.value = normalizeOptionList(data);
}

function fillFormFromRow(row) {
  form.id = row.id;
  form.appId = row.appId || "";
  form.name = row.name || "";
  form.deptId = row.deptId ?? null;
  form.ownerUserId = row.ownerUserId ?? null;
  form.remark = row.remark ?? "";
  form.rateLimitPolicy = normalizeRateLimitPolicy(row.rateLimitPolicy);
  form.quotaPolicy = normalizeQuotaPolicy(row.quotaPolicy);
}

function resetForm() {
  if (props.app) {
    fillFormFromRow(props.app);
  } else {
    form.id = null;
    form.appId = "";
    form.name = "";
    form.deptId = null;
    form.ownerUserId = null;
    form.remark = "";
    form.rateLimitPolicy = emptyRateLimitPolicy();
    form.quotaPolicy = emptyQuotaPolicy();
  }
}

watch(
    () => [props.modelValue, props.app],
    ([visible]) => {
      if (visible) {
        resetForm();
        loadDepartments();
        loadUsers();
      }
    }
);

function onClosed() {
  form.id = null;
}

function buildPayload() {
  const rateLimitPolicy = isEmptyRateLimitPolicy(form.rateLimitPolicy)
      ? null
      : normalizeRateLimitPolicy(form.rateLimitPolicy);
  const quotaPolicy = isEmptyQuotaPolicy(form.quotaPolicy)
      ? null
      : normalizeQuotaPolicy(form.quotaPolicy);
  return {
    appId: form.appId,
    name: form.name,
    deptId: form.deptId,
    ownerUserId: form.ownerUserId,
    remark: form.remark?.trim() || null,
    rateLimitPolicy,
    quotaPolicy
  };
}

async function submit() {
  if (!form.appId?.trim() || !form.name?.trim()) {
    ElMessage.warning("请填写 App ID 与名称");
    return;
  }
  saving.value = true;
  try {
    const payload = buildPayload();
    if (isEdit.value) {
      await updateApp(form.id, payload);
      ElMessage.success("已保存");
    } else {
      await createApp(payload);
      ElMessage.success("已创建");
    }
    emit("update:modelValue", false);
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>
