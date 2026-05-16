<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑折扣' : '新增折扣'"
    width="480px"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="100px">
      <el-form-item label="策略名">
        <el-input v-model="form.policyName" />
      </el-form-item>
      <el-form-item label="范围类型">
        <el-input v-model="form.scopeType" placeholder="GLOBAL / MODEL" />
      </el-form-item>
      <el-form-item label="范围 ID">
        <el-input-number v-model="form.scopeRefId" :min="0" style="width: 100%" />
      </el-form-item>
      <el-form-item label="折扣类型">
        <el-input v-model="form.discountType" placeholder="RATE" />
      </el-form-item>
      <el-form-item label="折扣值">
        <el-input-number v-model="form.discountValue" :step="0.01" style="width: 100%" />
      </el-form-item>
      <el-form-item label="优先级">
        <el-input-number v-model="form.priority" :min="0" style="width: 100%" />
      </el-form-item>
      <el-form-item label="状态">
        <el-input v-model="form.status" placeholder="ACTIVE" />
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
import { createDiscountPolicy, updateDiscountPolicy } from "../../api/providers";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  providerId: { type: Number, default: null },
  /** 传入策略行表示编辑，null 表示新增 */
  policy: { type: Object, default: null }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
const form = reactive({
  id: null,
  policyName: "",
  scopeType: "GLOBAL",
  scopeRefId: 0,
  discountType: "RATE",
  discountValue: 0.95,
  priority: 10,
  status: "ACTIVE"
});

const isEdit = computed(() => props.policy != null);

function resetForm() {
  if (props.policy) {
    const row = props.policy;
    form.id = row.id;
    form.policyName = row.policyName;
    form.scopeType = row.scopeType;
    form.scopeRefId = row.scopeRefId;
    form.discountType = row.discountType;
    form.discountValue = row.discountValue;
    form.priority = row.priority;
    form.status = row.status;
  } else {
    form.id = null;
    form.policyName = "";
    form.scopeType = "GLOBAL";
    form.scopeRefId = 0;
    form.discountType = "RATE";
    form.discountValue = 0.95;
    form.priority = 10;
    form.status = "ACTIVE";
  }
}

watch(
  () => [props.modelValue, props.policy],
  ([visible]) => {
    if (visible) resetForm();
  }
);

async function submit() {
  if (!props.providerId) return;
  saving.value = true;
  try {
    const body = {
      policyName: form.policyName,
      scopeType: form.scopeType,
      scopeRefId: form.scopeRefId,
      discountType: form.discountType,
      discountValue: form.discountValue,
      priority: form.priority,
      status: form.status
    };
    if (isEdit.value) {
      await updateDiscountPolicy(props.providerId, form.id, body);
    } else {
      await createDiscountPolicy(props.providerId, body);
    }
    ElMessage.success("已保存");
    emit("update:modelValue", false);
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>
