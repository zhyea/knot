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
      <el-form-item label="折扣类型">
        <el-radio-group v-model="form.discountType">
          <el-radio-button v-for="opt in discountTypeOptions" :key="opt.itemCode" :value="opt.itemCode">
            {{ opt.itemLabel }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="折扣值">
        <el-input-number v-model="form.discountValue" :step="0.01" style="width: 100%" />
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
  </el-dialog>
</template>

<script setup lang="ts">
import type {Dict} from "@/types";
import {type PropType, computed, onMounted, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import {useEnums} from "@/composables/useEnums";
import {createDiscountPolicy, updateDiscountPolicy} from "@/api/models";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  modelCode: {type: String, default: ""},
  /** 传入策略行表示编辑，null 表示新增 */
  policy: {type: Object as PropType<Dict | null>, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
/** 折扣类型（DB 枚举 category=discount_type）：PERCENTAGE / FIXED，单选框展示 */
const {options: discountTypeOptions, loadOptions} = useEnums("discount_type");
onMounted(loadOptions);

const form = reactive({
  id: null,
  discountType: "PERCENTAGE",
  discountValue: 0.95,
  status: 1
});

const isEdit = computed(() => props.policy != null);

function resetForm() {
  if (props.policy) {
    const row = props.policy;
    form.id = row.id;
    form.discountType = row.discountType;
    form.discountValue = row.discountValue;
    form.status = row.status;
  } else {
    form.id = null;
    form.discountType = "PERCENTAGE";
    form.discountValue = 0.95;
    form.status = 1;
  }
}

watch(
  () => [props.modelValue, props.policy],
  ([visible]) => {
    if (visible) resetForm();
  }
);

async function submit() {
  if (!props.modelCode) return;
  saving.value = true;
  try {
    const body = {
      discountType: form.discountType,
      discountValue: form.discountValue,
      status: form.status
    };
    if (isEdit.value) {
      await updateDiscountPolicy(props.modelCode, form.id!, body);
    } else {
      await createDiscountPolicy(props.modelCode, body);
    }
    ElMessage.success("已保存");
    emit("update:modelValue", false);
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>
