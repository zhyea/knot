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
        <el-input v-if="isEdit" :model-value="form.itemCode" disabled />
        <el-input v-else v-model="form.itemCode" placeholder="如 OPENAI" />
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
import { computed, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { createEnumConfig, updateEnumConfig } from "../../api/enums";
import { clearEnumCache } from "../../composables/useEnums";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 所属分类编码（必填） */
  category: { type: String, default: "" },
  /** 传入枚举项表示编辑，null 表示在该分类下新增 */
  item: { type: Object, default: null }
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

const isEdit = computed(() => props.item != null);

function resetForm() {
  if (props.item) {
    form.id = props.item.id;
    form.category = props.item.category;
    form.itemCode = props.item.itemCode;
    form.itemLabel = props.item.itemLabel;
    form.sortOrder = props.item.sortOrder ?? 0;
    form.isEnabled = props.item.isEnabled !== false;
    form.remark = props.item.remark || "";
  } else {
    form.id = null;
    form.category = props.category;
    form.itemCode = "";
    form.itemLabel = "";
    form.sortOrder = 0;
    form.isEnabled = true;
    form.remark = "";
  }
}

watch(
  () => [props.modelValue, props.category, props.item],
  ([visible]) => {
    if (visible) resetForm();
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
      await updateEnumConfig(form.id, payload);
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
