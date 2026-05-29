<template>
  <el-dialog v-model="visible" title="新建模板" width="520px" destroy-on-close>
    <el-form :model="form" label-width="100px">
      <el-form-item label="编码" required>
        <el-input v-model="form.code" />
      </el-form-item>
      <el-form-item label="名称" required>
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item label="渠道">
        <EnumSelect v-model="form.channel" category="channel" />
      </el-form-item>
      <el-form-item label="内容">
        <el-input v-model="form.content" type="textarea" :rows="5" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import EnumSelect from "../common/EnumSelect.vue";
import { createNotifyTemplate } from "../../api/notifications";

const props = defineProps({
  modelValue: { type: Boolean, default: false }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value)
});

const saving = ref(false);
const form = reactive({ code: "", name: "", channel: "EMAIL", content: "" });

watch(
  () => props.modelValue,
  (value) => {
    if (value) resetForm();
  }
);

function resetForm() {
  form.code = "";
  form.name = "";
  form.channel = "EMAIL";
  form.content = "";
}

async function submit() {
  if (!form.code?.trim() || !form.name?.trim()) {
    ElMessage.warning("请填写编码与名称");
    return;
  }
  saving.value = true;
  try {
    await createNotifyTemplate({
      id: null,
      code: form.code.trim(),
      name: form.name.trim(),
      channel: form.channel,
      content: form.content
    });
    ElMessage.success("已创建");
    visible.value = false;
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>
