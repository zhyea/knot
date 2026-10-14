<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    size="45%"
    class="drawer-with-scrollbar"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-scrollbar max-height="calc(100vh - 140px)">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="任务编码" prop="taskCode">
        <el-input v-model="form.taskCode" placeholder="如 operation-log-retention" :disabled="Boolean(task?.id)" />
      </el-form-item>
      <el-form-item label="任务名称" prop="taskName">
        <el-input v-model="form.taskName" placeholder="请输入任务名称" />
      </el-form-item>
      <el-form-item label="处理器" prop="handlerCode">
        <el-select
          v-model="form.handlerCode"
          filterable
          :loading="handlerLoading"
          placeholder="请选择处理器"
          class="handler-select"
        >
          <el-option v-for="code in handlerOptions" :key="code" :label="code" :value="code" />
        </el-select>
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
          <el-radio-button :label="EnabledStatus.ENABLED">启用</el-radio-button>
          <el-radio-button :label="EnabledStatus.DISABLED">禁用</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="说明">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入说明" />
      </el-form-item>
    </el-form>
    </el-scrollbar>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import type {Dict} from "@/types";
import {type PropType,  computed, reactive, ref, watch} from "vue";
import CronExpressionInput from "./CronExpressionInput.vue";
import {createScheduledTask, listScheduledTaskHandlers, updateScheduledTask} from "@/api/scheduledTasks";
import {EnabledStatus} from "@/constants/status";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  task: { type: Object as PropType<Dict | null>, default: null }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const formRef = ref();
const saving = ref(false);
const form = reactive(defaultForm());

/** 处理器下拉选项：取自后端已注册 handler bean，避免手输编码 */
const handlerOptions = ref<string[]>([]);
const handlerLoading = ref(false);

const title = computed(() => (props.task?.id ? "编辑任务配置" : "新增任务配置"));

const rules = {
  taskCode: [{ required: true, message: "请输入任务编码", trigger: "blur" }],
  taskName: [{ required: true, message: "请输入任务名称", trigger: "blur" }],
  handlerCode: [{ required: true, message: "请选择处理器", trigger: "change" }],
  cronExpression: [{ required: true, message: "请输入 Cron 表达式", trigger: "blur" }],
  executionMode: [{ required: true, message: "请选择执行模式", trigger: "change" }],
  status: [{ required: true, message: "请选择状态", trigger: "change" }]
};

watch(
  () => [props.modelValue, props.task],
  ([open]) => {
    if (open) {
      Object.assign(form, defaultForm(), props.task || {});
      void loadHandlerOptions();
    }
  },
  { immediate: true }
);

async function loadHandlerOptions() {
  handlerLoading.value = true;
  try {
    const codes = await listScheduledTaskHandlers();
    // 编辑态兜底：任务里存的 handler 若已不在后端注册表（handler 被摘除），仍补进选项，
    // 否则下拉会显示空白裸值，保存时被静默改写成第一个选项
    const list = Array.isArray(codes) ? codes.filter((code: string) => Boolean(code)) : [];
    if (form.handlerCode && !list.includes(form.handlerCode)) {
      list.push(form.handlerCode);
    }
    handlerOptions.value = list;
  } catch {
    handlerOptions.value = form.handlerCode ? [form.handlerCode] : [];
  } finally {
    handlerLoading.value = false;
  }
}

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
    status: EnabledStatus.ENABLED,
    description: ""
  };
}
</script>

<style scoped>
.handler-select {
  width: 100%;
}
</style>
