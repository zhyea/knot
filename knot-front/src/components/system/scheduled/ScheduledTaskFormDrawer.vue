<template>
  <el-drawer :model-value="modelValue" :title="title" size="45%" @update:model-value="emit('update:modelValue', $event)">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="任务编码" prop="taskCode">
        <el-input v-model="form.taskCode" placeholder="如 operation-log-retention" :disabled="Boolean(task?.id)" />
      </el-form-item>
      <el-form-item label="任务名称" prop="taskName">
        <el-input v-model="form.taskName" placeholder="请输入任务名称" />
      </el-form-item>
      <el-form-item label="处理器" prop="handlerCode">
        <el-input v-model="form.handlerCode" placeholder="如 OPERATION_LOG_RETENTION" />
      </el-form-item>
      <el-form-item label="Cron" prop="cronExpression">
        <CronExpressionInput v-model="form.cronExpression" />
      </el-form-item>
      <el-form-item label="执行模式" prop="executionMode">
        <el-radio-group v-model="form.executionMode">
          <el-radio-button label="SINGLE">单节点</el-radio-button>
          <el-radio-button label="BROADCAST">广播</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio-button label="ENABLED">启用</el-radio-button>
          <el-radio-button label="DISABLED">禁用</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="说明">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入说明" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import CronExpressionInput from "./CronExpressionInput.vue";
import { createScheduledTask, updateScheduledTask } from "../../../api/scheduledTasks";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  task: { type: Object, default: null }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const formRef = ref();
const saving = ref(false);
const form = reactive(defaultForm());

const title = computed(() => (props.task?.id ? "编辑任务配置" : "新增任务配置"));

const rules = {
  taskCode: [{ required: true, message: "请输入任务编码", trigger: "blur" }],
  taskName: [{ required: true, message: "请输入任务名称", trigger: "blur" }],
  handlerCode: [{ required: true, message: "请输入处理器", trigger: "blur" }],
  cronExpression: [{ required: true, message: "请输入 Cron 表达式", trigger: "blur" }],
  executionMode: [{ required: true, message: "请选择执行模式", trigger: "change" }],
  status: [{ required: true, message: "请选择状态", trigger: "change" }]
};

watch(
  () => [props.modelValue, props.task],
  ([open]) => {
    if (open) {
      Object.assign(form, defaultForm(), props.task || {});
    }
  },
  { immediate: true }
);

async function save() {
  await formRef.value?.validate();
  saving.value = true;
  try {
    if (props.task?.id) {
      await updateScheduledTask(props.task.id, form);
    } else {
      await createScheduledTask(form);
    }
    emit("saved");
    emit("update:modelValue", false);
  } finally {
    saving.value = false;
  }
}

function defaultForm() {
  return {
    taskCode: "",
    taskName: "",
    handlerCode: "",
    cronExpression: "0 0 3 * * ?",
    executionMode: "SINGLE",
    status: "ENABLED",
    description: ""
  };
}
</script>
