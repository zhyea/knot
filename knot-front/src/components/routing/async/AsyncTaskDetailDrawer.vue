<template>
  <el-drawer
    :model-value="modelValue"
    title="异步任务详情"
    size="70%"
    destroy-on-close
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <div v-loading="loading" class="async-task-detail">
      <template v-if="task">
        <div class="async-task-detail__header">
          <div class="async-task-detail__title">
            <span class="async-task-detail__code">{{ task.taskCode }}</span>
            <el-tag :type="statusTagType(task.status)" size="small">{{ statusLabel(task.status) }}</el-tag>
            <el-tag v-if="(task.retryRound ?? 0) > 0" type="warning" size="small">
              第 {{ task.retryRound }} 次重试
            </el-tag>
          </div>
          <div class="async-task-detail__actions">
            <el-button v-if="inFlight" type="warning" plain :loading="acting" @click="act('cancel')">
              终止
            </el-button>
            <el-button v-if="retryable" type="primary" plain :loading="acting" @click="act('retry')">
              重试
            </el-button>
            <el-button :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>

        <el-alert
          v-if="task.errorCode"
          class="async-task-detail__error"
          type="error"
          :closable="false"
          show-icon
          :title="`${task.errorCode}：${displayText(task.errorMessage)}`"
        />

        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="任务档案">{{ displayText(task.profileCode) }}</el-descriptions-item>
          <el-descriptions-item label="协议">{{ displayText(task.protocol) }}</el-descriptions-item>
          <el-descriptions-item label="上游任务号">{{ displayText(task.upstreamTaskId) }}</el-descriptions-item>
          <el-descriptions-item label="查询次数">{{ task.attempts ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="计费规则">{{ displayText(task.billingRuleCode) }}</el-descriptions-item>
          <el-descriptions-item label="规则版本">{{ displayText(task.billingRuleVersionCode) }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime(task.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatDateTime(task.completedAt) }}</el-descriptions-item>
          <el-descriptions-item label="knot 侧超时">{{ formatDateTime(task.expiresAt) }}</el-descriptions-item>
          <el-descriptions-item label="下次轮询">{{ formatDateTime(task.nextPollAt) }}</el-descriptions-item>
          <el-descriptions-item label="产物失效">{{ formatDateTime(task.resultExpiresAt) }}</el-descriptions-item>
          <el-descriptions-item label="轮询占位">{{ displayText(task.claimId) }}</el-descriptions-item>
        </el-descriptions>

        <!--
          回调交付是**独立维度**，单独一段而不是塞进上面那张表：
          「任务成功 + 回调失败」是合法组合，混排会让人读成「任务失败」。
          没有 callback_url 的行整段不渲染（deliveryStatus 恒为 0=无需回调，展示它只是占位）。
        -->
        <el-descriptions
          v-if="hasCallback"
          class="async-task-detail__delivery"
          :column="2"
          border
          size="small"
          title="回调交付"
        >
          <el-descriptions-item label="回调地址" :span="2">
            <span class="async-task-detail__url">{{ task.callbackUrl }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="交付状态">
            <el-tag :type="deliveryTagType(task.deliveryStatus)" size="small">
              {{ deliveryLabel(task.deliveryStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="投递次数">{{ task.deliveryAttempts ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="下次投递">{{ formatDateTime(task.nextDeliveryAt) }}</el-descriptions-item>
          <el-descriptions-item label="送达时间">{{ formatDateTime(task.deliveredAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="task.deliveryError" label="最后一次失败原因" :span="2">
            {{ task.deliveryError }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="deliveryFailed"
          class="async-task-detail__delivery-alert"
          type="warning"
          :closable="false"
          show-icon
          title="回调已放弃送达"
          :description="`连续 ${task?.deliveryAttempts ?? 0} 次投递失败。这不影响任务本身：客户端仍可用任务句柄轮询取结果。`"
        />

        <!--
          四段 JSON 按 tab 分栏而不是纵向堆：排查时通常只看其中一段，
          纵向堆会把「哪段是请求体、哪段是口径快照」变成要靠滚动记住的事。
        -->
        <el-tabs v-model="activeJsonTab" class="async-task-detail__json">
          <el-tab-pane label="原始请求体" name="request">
            <JsonCodeEditor :model-value="task.requestJson" readonly />
          </el-tab-pane>
          <el-tab-pane label="查询口径快照" name="spec">
            <JsonCodeEditor :model-value="task.pollSpecJson" readonly />
          </el-tab-pane>
          <el-tab-pane label="计费上下文" name="context">
            <JsonCodeEditor :model-value="task.contextJson" readonly />
          </el-tab-pane>
          <el-tab-pane label="终态产物" name="result">
            <JsonCodeEditor :model-value="task.resultJson" readonly />
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty v-else-if="!loading" description="没有读到任务详情" />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import {computed, ref, watch} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import {getAsyncTask, cancelAsyncTask, retryAsyncTask} from "@/api/asyncTasks";
import JsonCodeEditor from "@/components/common/JsonCodeEditor.vue";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {AsyncTaskDeliveryStatus, AsyncTaskStatus} from "@/constants/status";
import type {Dict} from "@/types";
import {displayText, formatDateTime} from "@/utils/format";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  taskCode: {type: String, default: ""}
});

const emit = defineEmits(["update:modelValue", "changed"]);

const {labelOf} = useEnumOptions();

const loading = ref(false);
const acting = ref(false);
const task = ref<Dict | null>(null);
const activeJsonTab = ref("request");

/** 在途：只有这两个状态能终止（与后端 `AsyncTaskStatusEnum#inFlight` 同一口径） */
const inFlight = computed(() => {
  const status = task.value?.status;
  return status === AsyncTaskStatus.PENDING || status === AsyncTaskStatus.POLLING;
});

/** 可重试：失败 / 超时（与后端 `retryableCodes` 同一口径） */
const retryable = computed(() => {
  const status = task.value?.status;
  return status === AsyncTaskStatus.FAILED || status === AsyncTaskStatus.EXPIRED;
});

/** 有回调地址才有「回调交付」这一段；没有就是无需回调，展示空字段只会让人以为是丢数据 */
const hasCallback = computed(() => Boolean(task.value?.callbackUrl));

/** 交付放弃（= 连续失败到上限）。⚠ 它不是任务失败，文案必须写清这一点 */
const deliveryFailed = computed(() => task.value?.deliveryStatus === AsyncTaskDeliveryStatus.FAILED);

function deliveryLabel(status: unknown): string {
  return labelOf("AsyncTaskDeliveryStatusEnum", status);
}

/** 交付态配色与任务态刻意不同轴：待回调用 warning 而不是「处理中」那种中性色，它是有待完成的事 */
function deliveryTagType(status: unknown): "success" | "warning" | "danger" | "info" {
  if (status === AsyncTaskDeliveryStatus.SUCCEEDED) {
    return "success";
  }
  if (status === AsyncTaskDeliveryStatus.PENDING) {
    return "warning";
  }
  if (status === AsyncTaskDeliveryStatus.FAILED) {
    return "danger";
  }
  return "info";
}

function statusLabel(status: unknown): string {
  return labelOf("AsyncTaskStatusEnum", status);
}

function statusTagType(status: unknown): "success" | "warning" | "danger" | "info" {
  if (status === AsyncTaskStatus.SUCCEEDED) {
    return "success";
  }
  if (status === AsyncTaskStatus.POLLING) {
    return "warning";
  }
  if (status === AsyncTaskStatus.FAILED) {
    return "danger";
  }
  return "info";
}

/**
 * 打开抽屉或换任务时重新拉一次详情。
 *
 * ⚠ 只在「打开」与「换 taskCode」两个时机拉：每次 modelValue 变化都拉会让
 * 「刚做完动作 → 刷新」重复请求两次（动作后本来就要 load 一次）。
 */
watch(
  () => [props.modelValue, props.taskCode] as const,
  ([visible]) => {
    if (visible && props.taskCode) {
      void load();
    }
  },
  {immediate: true}
);

async function load() {
  if (!props.taskCode) {
    return;
  }
  loading.value = true;
  try {
    task.value = (await getAsyncTask(props.taskCode)) as Dict;
  } catch {
    // 拦截器已经弹过错误提示；这里只保证不把上一次的任务留在界面上
    task.value = null;
  } finally {
    loading.value = false;
  }
}

/**
 * 执行运维动作。
 *
 * ⚠ <b>「没做成」也要提示，而且要用后端那句话</b>：终止/重试的失败多半不是异常
 * （任务已是终态、正被轮询节点处理），后端会用 200 + `changed=false` + message 回话。
 * 这里如果只判 Promise 成败，会把「没做成」当成功，运维会以为操作生效了。
 */
async function act(action: "cancel" | "retry") {
  const taskCode = task.value?.taskCode;
  if (!taskCode) {
    return;
  }
  const label = action === "cancel" ? "终止" : "重试";
  try {
    await ElMessageBox.confirm(
      action === "cancel"
        ? "终止后该任务不再轮询、不计费，且不可恢复。确认终止？"
        : "重试会重新轮询同一笔上游任务（不会重新提交），确认继续？",
      `确认${label}`,
      {type: "warning"}
    );
  } catch {
    return;
  }
  acting.value = true;
  try {
    const result = (await (action === "cancel" ? cancelAsyncTask(taskCode) : retryAsyncTask(taskCode))) as Dict;
    const message = String(result?.message ?? "");
    if (result?.changed === true) {
      ElMessage.success(message || `${label}成功`);
    } else {
      // 200 + changed=false 是「没做成但也不是错误」，必须让运维看到原因
      ElMessage.warning(message || `${label}未生效`);
    }
    await load();
    emit("changed");
  } catch {
    // 网关不可用 / 任务不存在：拦截器已提示，这里只需要保证不做无效刷新
  } finally {
    acting.value = false;
  }
}
</script>

<style scoped>
.async-task-detail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.async-task-detail__title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.async-task-detail__code {
  font-family: var(--el-font-family-monospace, monospace);
  font-weight: 600;
  word-break: break-all;
}

.async-task-detail__error {
  margin-bottom: 12px;
}

.async-task-detail__delivery {
  margin-top: 16px;
}

.async-task-detail__delivery-alert {
  margin-top: 8px;
}

/* 回调地址是客户端给的完整 URL，可能很长：等宽字体 + 任意位置折行，别撑破表格 */
.async-task-detail__url {
  font-family: var(--el-font-family-monospace, monospace);
  word-break: break-all;
}

.async-task-detail__json {
  margin-top: 16px;
}
</style>
