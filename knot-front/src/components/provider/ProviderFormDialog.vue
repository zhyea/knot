<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑供应商' : '新建供应商'"
    width="560px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >
    <el-form :model="form" label-width="100px">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" placeholder="供应商名称" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="form.type" placeholder="请选择供应商类型" clearable style="width: 100%">
          <el-option
            v-for="item in providerTypeOptions"
            :key="item.itemCode"
            :label="item.itemLabel"
            :value="item.itemCode"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="Base URL">
        <el-input v-model="form.baseUrl" placeholder="https://..." />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="form.enabled" />
      </el-form-item>
      <el-form-item label="频控策略">
        <KvEditor v-model="form.rateLimitPolicy" value-mode="number" />
      </el-form-item>
      <el-form-item label="额度策略">
        <KvEditor v-model="form.quotaPolicy" value-mode="number" />
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
import KvEditor from "../common/KvEditor.vue";
import { useEnums } from "../../composables/useEnums";
import { createProvider, updateProvider } from "../../api/providers";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 传入供应商行表示编辑，null 表示新建 */
  provider: { type: Object, default: null }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const { options: providerTypeOptions, loadOptions: loadProviderTypes } = useEnums("provider_type");
const saving = ref(false);
const form = reactive({
  id: null,
  name: "",
  type: "",
  baseUrl: "",
  enabled: true,
  rateLimitPolicy: {},
  quotaPolicy: {}
});

const isEdit = computed(() => props.provider != null);

function resetForm() {
  if (props.provider) {
    const row = props.provider;
    form.id = row.id;
    form.name = row.name;
    form.type = row.type;
    form.baseUrl = row.baseUrl || "";
    form.enabled = !!row.enabled;
    form.rateLimitPolicy =
      row.rateLimitPolicy && typeof row.rateLimitPolicy === "object" ? { ...row.rateLimitPolicy } : {};
    form.quotaPolicy = row.quotaPolicy && typeof row.quotaPolicy === "object" ? { ...row.quotaPolicy } : {};
  } else {
    form.id = null;
    form.name = "";
    form.type = "";
    form.baseUrl = "";
    form.enabled = true;
    form.rateLimitPolicy = {};
    form.quotaPolicy = {};
  }
}

watch(
  () => [props.modelValue, props.provider],
  ([visible]) => {
    if (visible) {
      resetForm();
      loadProviderTypes();
    }
  }
);

function onClosed() {
  form.id = null;
}

function buildPayload() {
  return {
    name: form.name,
    type: form.type,
    baseUrl: form.baseUrl,
    enabled: form.enabled,
    rateLimitPolicy: Object.keys(form.rateLimitPolicy).length ? form.rateLimitPolicy : null,
    quotaPolicy: Object.keys(form.quotaPolicy).length ? form.quotaPolicy : null
  };
}

async function submit() {
  if (!form.name?.trim()) {
    ElMessage.warning("请填写名称");
    return;
  }
  saving.value = true;
  try {
    const payload = buildPayload();
    if (isEdit.value) {
      await updateProvider(form.id, payload);
      ElMessage.success("已保存");
    } else {
      await createProvider(payload);
      ElMessage.success("已创建");
    }
    emit("update:modelValue", false);
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>
