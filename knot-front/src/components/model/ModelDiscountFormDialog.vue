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
        <!-- precision 对齐库列 DECIMAL(10,4)：不加会让 0.01 步进的浮点尾数（0.9400000000000001）原样提交 -->
        <el-input-number
          v-model="form.discountValue"
          :step="0.01"
          :precision="4"
          :min="0"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-switch
          v-model="form.status"
          :active-value="EnabledStatus.ENABLED"
          :inactive-value="EnabledStatus.DISABLED"
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
import {createDiscountPolicy, getDiscountPolicy, updateDiscountPolicy} from "@/api/models";
import {EnabledStatus} from "@/constants/status";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  modelCode: {type: String, default: ""},
  /** 编辑态只接主键：打开时按 modelCode + policyId 拉详情，不复用列表行数据 */
  policyId: {type: Number as PropType<number | null>, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
/** 折扣类型（DB 枚举 category=discount_type）：PERCENTAGE / FIXED，单选框展示 */
const {options: discountTypeOptions, loadOptions} = useEnums("discount_type");
onMounted(() => loadOptions(true));

const form = reactive({
  id: null,
  discountType: "PERCENTAGE",
  discountValue: 0.95,
  status: 1
});

const isEdit = computed(() => props.policyId != null);

function resetForm(detail: Dict | null): void {
  form.id = detail?.id ?? null;
  form.discountType = detail?.discountType || "PERCENTAGE";
  form.discountValue = detail?.discountValue ?? 0.95;
  form.status = detail?.status ?? 1;
}

watch(
  () => [props.modelValue, props.modelCode, props.policyId] as const,
  async ([visible]) => {
    if (!visible) return;
    if (props.policyId == null) {
      resetForm(null);
      return;
    }
    let detail: Dict;
    try {
      const loaded = await getDiscountPolicy(props.modelCode, props.policyId);
      if (!loaded) throw new Error("折扣策略详情为空");
      detail = loaded as Dict;
    } catch {
      ElMessage.error("折扣策略详情加载失败");
      emit("update:modelValue", false);
      return;
    }
    resetForm(detail);
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
      await updateDiscountPolicy(props.modelCode, props.policyId!, body);
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
