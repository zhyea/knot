<template>
  <el-drawer
    :model-value="modelValue"
    :title="editing ? '编辑供应商信息' : '新建供应商信息'"
    size="50%"
    class="drawer-with-scrollbar"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
    @closed="onClosed"
  >
    <el-scrollbar max-height="calc(100vh - 140px)">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" class="drawer-form">
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" maxlength="32" placeholder="例如 openai" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="100" placeholder="例如 OpenAI" />
        </el-form-item>
        <el-form-item label="分类" prop="tags">
          <el-select v-model="form.tags" multiple filterable allow-create default-first-option clearable
            placeholder="可多选，也可直接输入自定义分类" style="width: 100%">
            <el-option v-for="item in tagPreset" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-scrollbar>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import {
  checkProviderProfileCode,
  createProviderProfile,
  updateProviderProfile
} from "../../api/providerProfiles";

const props = defineProps({
  modelValue: Boolean,
  providerProfile: { type: Object, default: null }
});
const emit = defineEmits(["update:modelValue", "saved"]);

const tagPreset = ["原厂", "云厂商", "代理"];
const submitting = ref(false);
const formRef = ref(null);
const form = reactive({ code: "", name: "", tags: [] });
const editing = ref(null);

const rules = {
  code: [
    { required: true, message: "请输入编码", trigger: "blur" },
    {
      validator: async (rule, value, callback) => {
        if (!value) return callback();
        try {
          const result = await checkProviderProfileCode(value, editing.value?.id);
          callback(result?.available ? undefined : new Error("编码已被占用"));
        } catch {
          callback();
        }
      },
      trigger: "blur"
    }
  ],
  name: [{ required: true, message: "请输入名称", trigger: "blur" }],
  tags: [{
    validator: (rule, value, callback) => {
      const list = (value || []).map((item) => String(item).trim()).filter(Boolean);
      callback(list.length ? undefined : new Error("请至少选择一个分类"));
    },
    trigger: "change"
  }]
};

watch(
  () => [props.modelValue, props.providerProfile],
  ([visible, row]) => {
    if (!visible) return;
    editing.value = row;
    form.code = row?.code || "";
    form.name = row?.name || "";
    form.tags = splitTags(row?.tag);
  },
  { immediate: true }
);

function splitTags(raw) {
  return raw ? String(raw).split(",").map((item) => item.trim()).filter(Boolean) : [];
}

function onClosed() {
  editing.value = null;
  formRef.value?.clearValidate();
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  submitting.value = true;
  try {
    const payload = {
      code: form.code.trim(),
      name: form.name.trim(),
      tag: form.tags.map((item) => String(item).trim()).filter(Boolean).join(",")
    };
    if (editing.value?.id) await updateProviderProfile(editing.value.id, payload);
    else await createProviderProfile(payload);
    ElMessage.success("保存成功");
    emit("update:modelValue", false);
    emit("saved");
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.drawer-form {
  padding-right: 8px;
}
</style>
