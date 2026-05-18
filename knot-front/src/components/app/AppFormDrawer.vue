<template>
  <el-drawer
      :model-value="modelValue"
      :title="isEdit ? '编辑应用' : '新建应用'"
      size="45%"
      destroy-on-close
      @update:model-value="emit('update:modelValue', $event)"
      @closed="onClosed"
  >
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
            <el-form-item label="负责人">
              <el-select
                  v-model="form.ownerUserId"
                  placeholder="请选择负责人"
                  clearable
                  filterable
                  style="width: 100%"
              >
                <el-option
                    v-for="user in userOptions"
                    :key="user.id"
                    :label="userLabel(user)"
                    :value="user.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="选填"/>
        </el-form-item>
      </div>

      <div class="space-line"/>

      <div class="slot-body">
        <el-form-item label="频控策略">
          <KvEditor v-model="form.rateLimitPolicy" value-mode="number"/>
        </el-form-item>
        <el-form-item label="额度策略">
          <KvEditor v-model="form.quotaPolicy" value-mode="number"/>
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import {computed, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import KvEditor from "../common/KvEditor.vue";
import {
  emptyQuotaPolicy,
  emptyRateLimitPolicy,
  isEmptyQuotaPolicy,
  isEmptyRateLimitPolicy,
  normalizeQuotaPolicy,
  normalizeRateLimitPolicy
} from "../../utils/trafficPolicy";
import {createApp, updateApp} from "../../api/apps";
import {listUsers} from "../../api/users";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  app: {type: Object, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
const userOptions = ref([]);

const form = reactive({
  id: null,
  appId: "",
  name: "",
  ownerUserId: null,
  remark: "",
  rateLimitPolicy: emptyRateLimitPolicy(),
  quotaPolicy: emptyQuotaPolicy()
});

const isEdit = computed(() => props.app != null);

function userLabel(user) {
  const name = user.realName?.trim() || user.username;
  return name === user.username ? name : `${name}（${user.username}）`;
}

async function loadUsers() {
  const data = await listUsers({pageNum: 1, pageSize: 500});
  userOptions.value = Array.isArray(data?.list) ? data.list : Array.isArray(data) ? data : [];
}

function fillFormFromRow(row) {
  form.id = row.id;
  form.appId = row.appId || "";
  form.name = row.name || "";
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
