<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="620px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="localForm" label-width="96px">
      <template v-for="field in fields" :key="field.key">
        <el-form-item :label="field.label" :required="field.required">
          <el-input
            v-if="field.type === 'textarea'"
            v-model="localForm[field.key]"
            type="textarea"
            :rows="field.rows || 3"
            :maxlength="field.maxlength"
            :show-word-limit="!!field.maxlength"
            :placeholder="field.placeholder"
          />
          <el-input-number
            v-else-if="field.type === 'number'"
            v-model="localForm[field.key]"
            :min="field.min ?? 0"
            :max="field.max ?? 999999"
            style="width: 100%"
          />
          <el-switch
            v-else-if="field.type === 'switch'"
            v-model="localForm[field.key]"
            :active-value="field.activeValue ?? 'ENABLED'"
            :inactive-value="field.inactiveValue ?? 'DISABLED'"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
          />
          <el-select
            v-else-if="field.type === 'select'"
            v-model="localForm[field.key]"
            filterable
            clearable
            :placeholder="field.placeholder"
            style="width: 100%"
          >
            <el-option
              v-for="option in field.options || []"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-tree-select
            v-else-if="field.type === 'tree-select'"
            v-model="localForm[field.key]"
            :data="field.options || []"
            :props="field.props || defaultTreeProps"
            :node-key="field.nodeKey || 'value'"
            :placeholder="field.placeholder"
            :check-strictly="field.checkStrictly !== false"
            clearable
            filterable
            style="width: 100%"
          />
          <el-input
            v-else
            v-model="localForm[field.key]"
            :maxlength="field.maxlength"
            :placeholder="field.placeholder"
          />
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import {reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  title: {type: String, default: ""},
  form: {type: Object, default: () => ({})},
  fields: {type: Array, default: () => []},
  submitter: {type: Function, required: true}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const saving = ref(false);
const localForm = reactive({});
const defaultTreeProps = {
  label: "label",
  value: "value",
  children: "children"
};

function resetForm() {
  for (const key of Object.keys(localForm)) {
    delete localForm[key];
  }
  const source = props.form || {};
  for (const field of props.fields) {
    if (source[field.key] !== undefined && source[field.key] !== null) {
      localForm[field.key] = source[field.key];
    } else if (field.defaultValue !== undefined) {
      localForm[field.key] = field.defaultValue;
    } else if (field.type === "number") {
      localForm[field.key] = 0;
    } else if (field.type === "switch") {
      localForm[field.key] = field.activeValue ?? "ENABLED";
    } else {
      localForm[field.key] = "";
    }
  }
}

watch(
  () => [props.modelValue, props.form, props.fields],
  ([visible]) => {
    if (visible) {
      resetForm();
    }
  },
  {immediate: true}
);

async function submit() {
  for (const field of props.fields) {
    if (field.required) {
      const value = localForm[field.key];
      if (value == null || `${value}`.trim?.() === "") {
        ElMessage.warning(`请填写${field.label}`);
        return;
      }
    }
  }
  saving.value = true;
  try {
    const payload = {};
    for (const field of props.fields) {
      const value = localForm[field.key];
      payload[field.key] = typeof value === "string" ? value.trim() : value;
    }
    const saved = await props.submitter(payload);
    ElMessage.success("保存成功");
    emit("update:modelValue", false);
    emit("saved", saved);
  } finally {
    saving.value = false;
  }
}
</script>
