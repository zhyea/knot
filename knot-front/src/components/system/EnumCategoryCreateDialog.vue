<template>
  <el-dialog
    :model-value="modelValue"
    title="新增分类首项"
    width="520px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="90px">
      <el-form-item label="分类编码" required>
        <el-input v-model="form.category" placeholder="如 provider_type" />
      </el-form-item>
      <el-form-item label="枚举编码" required>
        <el-input v-model="form.itemCode" placeholder="如 OPENAI" />
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

<script setup>
import { reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { createEnumConfig } from "../../api/enums";
import { clearEnumCache } from "../../composables/useEnums";

const props = defineProps({
  modelValue: { type: Boolean, default: false }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
const form = reactive({
  category: "",
  itemCode: "",
  itemLabel: "",
  sortOrder: 0,
  isEnabled: true,
  remark: ""
});

function resetForm() {
  form.category = "";
  form.itemCode = "";
  form.itemLabel = "";
  form.sortOrder = 0;
  form.isEnabled = true;
  form.remark = "";
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) resetForm();
  }
);

async function submit() {
  if (!form.category?.trim() || !form.itemCode?.trim() || !form.itemLabel?.trim()) {
    ElMessage.warning("请填写分类编码、枚举编码和显示名");
    return;
  }
  saving.value = true;
  try {
    await createEnumConfig({
      category: form.category.trim(),
      itemCode: form.itemCode.trim(),
      itemLabel: form.itemLabel.trim(),
      sortOrder: form.sortOrder,
      isEnabled: form.isEnabled,
      remark: form.remark
    });
    ElMessage.success("已创建");
    clearEnumCache();
    emit("update:modelValue", false);
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>
