<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="640px"
    destroy-on-close
    class="json-code-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <JsonCodeEditor
      :model-value="formattedJson"
      readonly
      min-height="240px"
      max-height="60vh"
    />
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from "vue";
import JsonCodeEditor from "./JsonCodeEditor.vue";
import { formatJsonText } from "../../utils/format";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: "JSON 预览" },
  data: { type: [Object, Array, String], default: null }
});

const emit = defineEmits(["update:modelValue"]);

const formattedJson = computed(() => {
  if (props.data == null) {
    return "";
  }
  return formatJsonText(props.data, 2);
});
</script>
