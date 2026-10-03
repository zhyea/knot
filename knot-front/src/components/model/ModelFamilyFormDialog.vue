<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑模型族' : '新建模型族'"
    width="560px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="96px">
      <el-form-item label="族编码" required :error="codeError">
        <el-input
          v-if="!isEdit"
          v-model="form.code"
          placeholder="如 gpt / qwen（小写字母、数字、-、_）"
          maxlength="64"
          @blur="validateCode"
        />
        <el-input v-else :model-value="form.code" disabled />
      </el-form-item>
      <el-form-item label="名称" required>
        <el-input v-model="form.name" placeholder="如 GPT（OpenAI）" maxlength="128" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="form.enabled" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import type {Row} from "@/types";
import {type PropType, computed, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import {
  checkModelFamilyCode,
  createModelFamily,
  updateModelFamily
} from "@/api/modelFamilies";
import {clearEnumCache} from "@/composables/useEnums";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  /** 传入行表示编辑；null 表示新建 */
  family: {type: Object as PropType<Row | null>, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
const codeError = ref("");
const form = reactive({
  id: null as number | string | null,
  code: "",
  name: "",
  sortOrder: 0,
  enabled: true,
  remark: ""
});

const isEdit = computed(() => props.family != null);

function resetForm() {
  codeError.value = "";
  if (props.family) {
    form.id = props.family.id ?? null;
    form.code = props.family.code || "";
    form.name = props.family.name || "";
    form.sortOrder = props.family.sortOrder ?? 0;
    form.enabled = props.family.enabled !== false;
    form.remark = props.family.remark || "";
  } else {
    form.id = null;
    form.code = "";
    form.name = "";
    form.sortOrder = 0;
    form.enabled = true;
    form.remark = "";
  }
}

watch(() => [props.modelValue, props.family], ([visible]) => {
  if (visible) resetForm();
});

async function validateCode() {
  codeError.value = "";
  const code = form.code.trim();
  if (!code) {
    return;
  }
  try {
    const res = await checkModelFamilyCode(code, form.id) as { available?: boolean };
    if (res && res.available === false) {
      codeError.value = "该族编码已存在";
    }
  } catch {
    // 校验失败不阻断提交，交由后端最终判定
  }
}

async function submit() {
  codeError.value = "";
  const code = form.code.trim();
  const name = form.name.trim();
  if (!code || !name) {
    ElMessage.warning("请填写族编码与名称");
    return;
  }

  if (!isEdit.value) {
    try {
      const res = await checkModelFamilyCode(code, null) as { available?: boolean };
      if (res && res.available === false) {
        codeError.value = "该族编码已存在";
        return;
      }
    } catch {
      // 忽略，交给后端判定
    }
  }

  const payload = {
    code,
    name,
    sortOrder: form.sortOrder,
    enabled: form.enabled,
    remark: form.remark
  };

  saving.value = true;
  try {
    if (isEdit.value) {
      await updateModelFamily(form.id!, payload);
      ElMessage.success("已保存");
    } else {
      await createModelFamily(payload);
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
