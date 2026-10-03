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
        <el-input v-model="form.category" placeholder="如 billing_unit" />
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

<script setup lang="ts">
import {reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import {createEnumConfig} from "@/api/enums";
import {clearEnumCache} from "@/composables/useEnums";

const props = defineProps({
  modelValue: { type: Boolean, default: false }
});

const emit = defineEmits(["update:modelValue", "saved"]);

/**
 * 禁止在 DB 重建的分类编码（与后端 EnumConfigService 的两组保留字保持一致）：
 * ① 已由代码枚举接管的分类（双源漂移风险）；
 * ② 已退役的零消费孤儿分类（误配置入口）。
 * 若将来要启用某个孤儿分类，应走「新建 Java enum + 注册 EnumOptionRegistry」，
 * 而不是在这里手工填回字典表。
 */
const RESERVED_CATEGORY_CODES = [
  // 代码枚举已接管
  "app_type",
  "billing_unit",
  "billing_currency",
  "plugin_scope_type",
  "status",
  "logical_model_visibility",
  "logical_model_publish_status",
  "model_pool_selection_strategy",
  "plugin_extension_point",
  "plugin_stage_code",
  // 零消费孤儿分类（2026-10-03 退役）
  "plugin_source_type",
  "alert_level",
  "risk_level",
  "plugin_fail_mode",
  "plugin_result_status"
];

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
  const category = form.category.trim();
  if (RESERVED_CATEGORY_CODES.includes(category)) {
    ElMessage.warning(`枚举分类 ${category} 已退役（代码枚举接管或零消费孤儿），禁止在 DB 重建`);
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
