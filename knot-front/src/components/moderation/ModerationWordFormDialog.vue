<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑敏感词' : '新建敏感词'"
    width="560px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="96px">
      <el-form-item label="敏感词" required :error="wordError">
        <el-input
          v-model="form.word"
          placeholder="原始词条，支持 emoji / 全角"
          maxlength="255"
          show-word-limit
          @blur="validateWord"
        />
      </el-form-item>
      <el-form-item label="分类" required>
        <EnumSelect v-model="form.categoryCode" category="moderation_category" placeholder="请选择分类" />
      </el-form-item>
      <el-form-item label="严重度" required>
        <el-radio-group v-model="form.severity">
          <el-radio-button :value="1">低</el-radio-button>
          <el-radio-button :value="2">中</el-radio-button>
          <el-radio-button :value="3">高</el-radio-button>
        </el-radio-group>
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
import EnumSelect from "../common/EnumSelect.vue";
import {
  checkModerationWord,
  createModerationWord,
  updateModerationWord
} from "@/api/moderationWords";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  /** 传入行表示编辑；null 表示新建 */
  word: {type: Object as PropType<Row | null>, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
const wordError = ref("");
const form = reactive({
  id: null as number | string | null,
  word: "",
  categoryCode: "",
  severity: 2,
  enabled: true,
  remark: ""
});

const isEdit = computed(() => props.word != null);

function resetForm() {
  wordError.value = "";
  if (props.word) {
    form.id = props.word.id ?? null;
    form.word = props.word.word || "";
    form.categoryCode = props.word.categoryCode || "";
    form.severity = props.word.severity ?? 2;
    form.enabled = props.word.enabled !== false;
    form.remark = props.word.remark || "";
  } else {
    form.id = null;
    form.word = "";
    form.categoryCode = "";
    form.severity = 2;
    form.enabled = true;
    form.remark = "";
  }
}

watch(() => [props.modelValue, props.word], ([visible]) => {
  if (visible) resetForm();
});

async function validateWord() {
  wordError.value = "";
  const word = form.word.trim();
  if (!word || !form.categoryCode) {
    return;
  }
  try {
    const res = await checkModerationWord(word, form.categoryCode, form.id) as { available?: boolean };
    if (res && res.available === false) {
      wordError.value = "同分类下已存在相同敏感词";
    }
  } catch {
    // 校验失败不阻断提交，交由后端最终判定
  }
}

async function submit() {
  wordError.value = "";
  const word = form.word.trim();
  if (!word) {
    ElMessage.warning("请填写敏感词");
    return;
  }
  if (!form.categoryCode) {
    ElMessage.warning("请选择分类");
    return;
  }

  try {
    const res = await checkModerationWord(word, form.categoryCode, form.id) as { available?: boolean };
    if (res && res.available === false) {
      wordError.value = "同分类下已存在相同敏感词";
      return;
    }
  } catch {
    // 忽略，交给后端判定
  }

  const payload = {
    word,
    categoryCode: form.categoryCode,
    severity: form.severity,
    enabled: form.enabled,
    remark: form.remark
  };

  saving.value = true;
  try {
    if (isEdit.value) {
      await updateModerationWord(form.id!, payload);
      ElMessage.success("已保存");
    } else {
      await createModerationWord(payload);
      ElMessage.success("已创建");
    }
    emit("update:modelValue", false);
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>
