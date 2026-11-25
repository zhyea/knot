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
            <el-input v-model="form.appCode" :disabled="codeLocked"/>
            <div v-if="codeLocked" class="form-tip">{{ codeLockHint }}</div>
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
                  v-model="form.deptCode"
                  :load-function="loadDepartmentOptions"
                  placeholder="请选择部门"
                  clearable
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
            <el-form-item label="负责人">
              <RemoteEntitySelect
                v-model="form.ownerUsername"
                :load-function="loadUserUsernameOptions"
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
          description="按统计窗口约束该应用可消耗的 Token 总量与成本上限，可叠加多个不同窗口。"
          v-model:quotas="form.quotaPolicies"
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
  emptyQuotaPolicies,
  isEmptyQuotaPolicy,
  normalizeQuotaPolicy,
  normalizeQuotaPolicies
} from "@/utils/trafficPolicy";
import {createApp, getApp, updateApp} from "@/api/apps";
import {listDepartmentOptions, listUserUsernameOptions} from "@/api/options";
import {toOptionsLoader} from "@/utils/options";
import type {Dict} from "@/types";
import {useMissingOptionGuard} from "@/composables/useMissingOptionGuard";
import {useCodeBindingLock} from "@/composables/useCodeBindingLock";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  /** 编辑态只接主键：打开时按 id 拉详情，不复用列表行数据 */
  appId: {type: Number as PropType<number | null>, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
const loadDepartmentOptions = toOptionsLoader(listDepartmentOptions);
const loadUserUsernameOptions = toOptionsLoader(listUserUsernameOptions);

const form = reactive({
  id: null,
  appCode: "",
  name: "",
  deptCode: null,
  ownerUsername: null,
  remark: "",
  quotaPolicies: emptyQuotaPolicies()
});

const isEdit = computed(() => props.appId != null);
/** 应用编码引用处数（绑定该应用的路由规则数）：>0 时不可改 */
const boundCount = ref(0);
const {codeLocked, codeLockHint} = useCodeBindingLock(() => isEdit.value, () => boundCount.value);

function resetForm(detail: Dict | null): void {
  form.id = detail?.id ?? null;
  form.appCode = detail?.appCode || "";
  form.name = detail?.name || "";
  form.deptCode = detail?.deptCode ?? null;
  form.ownerUsername = detail?.ownerUsername ?? null;
  form.remark = detail?.remark ?? "";
  form.quotaPolicies = detail ? normalizeQuotaPolicies(detail.quotaPolicies) : emptyQuotaPolicies();
  boundCount.value = Number(detail?.boundCount ?? 0);
}

watch(
  () => [props.modelValue, props.appId] as const,
  async ([visible]) => {
    if (!visible) return;
    if (props.appId == null) {
      resetForm(null);
      return;
    }
    let detail: Dict;
    try {
      const loaded = await getApp(props.appId);
      if (!loaded) throw new Error("应用详情为空");
      detail = loaded as Dict;
    } catch {
      ElMessage.error("应用详情加载失败");
      emit("update:modelValue", false);
      return;
    }
    resetForm(detail);
  }
);

function onClosed() {
  form.id = null;
}

function buildPayload() {
  const quotaPolicies = form.quotaPolicies
    .map(normalizeQuotaPolicy)
    .filter((q) => !isEmptyQuotaPolicy(q));
  return {
    appCode: form.appCode,
    name: form.name,
    deptCode: form.deptCode,
    ownerUsername: form.ownerUsername,
    remark: form.remark?.trim() || null,
    quotaPolicies
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

<style scoped>
</style>
