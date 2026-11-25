<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑枚举值' : '新增枚举值'"
    width="520px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="90px">
      <el-form-item label="分类" required>
        <el-input :model-value="form.category" disabled />
      </el-form-item>
      <el-form-item label="编码" required>
        <el-input v-model="form.itemCode" :disabled="codeLocked" placeholder="如 OPENAI" />
        <div v-if="codeLocked" class="form-tip form-tip--secondary">{{ codeLockHint }}</div>
      </el-form-item>
      <el-form-item label="显示名" required>
        <el-input v-model="form.itemLabel" placeholder="如 OpenAI" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.sortOrder" :min="0" />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="form.isEnabled" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
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
import {type PropType,  computed, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import {createEnumConfig, getEnumItem, updateEnumConfig} from "@/api/enums";
import {clearEnumCache} from "@/composables/useEnums";
import {useCodeBindingLock} from "@/composables/useCodeBindingLock";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 所属分类编码（必填） */
  category: { type: String, default: "" },
  /** 编辑态只接主键：打开时按 itemId 拉详情，不复用列表抽屉里的行数据 */
  itemId: { type: Number as PropType<number | null>, default: null }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
const form = reactive({
  id: null,
  category: "",
  itemCode: "",
  itemLabel: "",
  sortOrder: 0,
  isEnabled: true,
  remark: ""
});

const isEdit = computed(() => props.itemId != null);

/**
 * 编码口径同其它业务码：仅在「编辑态且已被引用」时锁定。
 * boundCount 由详情端点下发（计费规则/统一模型的模型族 + 折扣类型 + 敏感词分类引用处数）。
 */
const boundCount = ref(0);
const {codeLocked, codeLockHint} = useCodeBindingLock(() => isEdit.value, () => boundCount.value);

function resetForm(detail: Dict | null): void {
  form.id = detail?.id ?? null;
  form.category = detail?.category || props.category;
  form.itemCode = detail?.itemCode || "";
  form.itemLabel = detail?.itemLabel || "";
  form.sortOrder = detail?.sortOrder ?? 0;
  form.isEnabled = detail ? detail.isEnabled !== false : true;
  form.remark = detail?.remark || "";
  boundCount.value = Number(detail?.boundCount ?? 0);
}

watch(
  () => [props.modelValue, props.category, props.itemId] as const,
  async ([visible]) => {
    if (!visible) return;
    if (props.itemId == null) {
      resetForm(null);
      return;
    }
    let detail: Dict;
    try {
      const loaded = await getEnumItem(props.itemId);
      if (!loaded) throw new Error("枚举项详情为空");
      detail = loaded as Dict;
    } catch {
      ElMessage.error("枚举项详情加载失败");
      emit("update:modelValue", false);
      return;
    }
    resetForm(detail);
  }
);

async function submit() {
  if (!form.category?.trim() || !form.itemCode?.trim() || !form.itemLabel?.trim()) {
    ElMessage.warning("请填写分类、编码和显示名");
    return;
  }
  saving.value = true;
  try {
    const payload = {
      category: form.category.trim(),
      itemCode: form.itemCode.trim(),
      itemLabel: form.itemLabel.trim(),
      sortOrder: form.sortOrder,
      isEnabled: form.isEnabled,
      remark: form.remark
    };
    if (isEdit.value) {
      await updateEnumConfig(form.id!, payload);
      ElMessage.success("已保存");
    } else {
      await createEnumConfig(payload);
      ElMessage.success("已创建");
    }
    clearEnumCache();
    emit("update:modelValue", false);
    emit("saved");
    } finally {
      saving.value = false;
    }
  }
</script>
