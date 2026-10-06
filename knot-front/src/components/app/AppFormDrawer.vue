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
          <el-form-item label="App Code" required>
            <el-input v-model="form.appCode" :disabled="isEdit"/>
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
                  :load-function="loadDepartmentOptions"
                  value-key="value"
                  :code-only="false"
                  label-key="label"
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
                  :load-function="loadUserOptions"
                  value-key="value"
                  :code-only="false"
                  label-key="label"
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
          mode="quota"
          title="限额配置"
          description="按统计窗口约束该应用可消耗的 Token 总量与成本上限。"
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

<script setup lang="ts">
import {type PropType, computed, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import TrafficPolicySection from "../common/TrafficPolicySection.vue";
import {
  emptyQuotaPolicy,
  isEmptyQuotaPolicy,
  normalizeQuotaPolicy
} from "@/utils/trafficPolicy";
import {createApp, updateApp} from "@/api/apps";
import {listDepartmentOptions, listUserOptions} from "@/api/options";
import {toOptionsLoader} from "@/utils/options";
import type {Dict, Row} from "@/types";
import {useMissingOptionGuard} from "@/composables/useMissingOptionGuard";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  app: {type: Object as PropType<Dict | null>, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
const loadDepartmentOptions = toOptionsLoader(listDepartmentOptions);
const loadUserOptions = toOptionsLoader(listUserOptions);

const form = reactive({
  id: null,
  appCode: "",
  name: "",
  deptId: null,
  ownerUserId: null,
  remark: "",
  quotaPolicy: emptyQuotaPolicy()
});

const isEdit = computed(() => props.app != null);

function fillFormFromRow(row: Row): void {
  form.id = row.id;
  form.appCode = row.appCode || "";
  form.name = row.name || "";
  form.deptId = row.deptId ?? null;
  form.ownerUserId = row.ownerUserId ?? null;
  form.remark = row.remark ?? "";
  form.quotaPolicy = normalizeQuotaPolicy(row.quotaPolicy);
}

function resetForm() {
  if (props.app) {
    fillFormFromRow(props.app);
  } else {
    form.id = null;
    form.appCode = "";
    form.name = "";
    form.deptId = null;
    form.ownerUserId = null;
    form.remark = "";
    form.quotaPolicy = emptyQuotaPolicy();
  }
}

watch(
  () => [props.modelValue, props.app],
  ([visible]) => {
    if (visible) {
      resetForm();
    }
  }
);

function onClosed() {
  form.id = null;
}

function buildPayload() {
  const quotaPolicy = isEmptyQuotaPolicy(form.quotaPolicy)
    ? null
    : normalizeQuotaPolicy(form.quotaPolicy);
  return {
    appCode: form.appCode,
    name: form.name,
    deptId: form.deptId,
    ownerUserId: form.ownerUserId,
    remark: form.remark?.trim() || null,
    quotaPolicy
  };
}

// 下拉缺失值守卫：已选项被删除/无权限时阻止提交（options 契约第 10 条）
const missing = useMissingOptionGuard();

async function submit() {
  if (missing.hasMissing.value) {
    ElMessage.warning(missing.hint.value);
    return;
  }
  if (!form.appCode?.trim() || !form.name?.trim()) {
    ElMessage.warning("请填写 App Code 与名称");
    return;
  }
  saving.value = true;
  try {
    const payload = buildPayload();
    if (isEdit.value) {
      await updateApp(form.id!, payload);
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
