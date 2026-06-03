<template>
  <el-drawer
    v-model="visible"
    :title="consumer ? '编辑消费者' : '新建消费者'"
    size="55%"
    class="drawer-with-scrollbar"
    destroy-on-close
    @closed="onClosed"
  >
    <el-scrollbar max-height="calc(100vh - 140px)">
    <el-form :model="form" label-width="118px" class="consumer-form">
      <div class="slot-body consumer-section">
        <div class="section-head">
          <div>
            <h3>基础信息</h3>
            <p>维护消费者编码、名称和归属用户。</p>
          </div>
          <el-form-item label="启用" class="inline-switch">
            <el-switch v-model="form.enabled" />
          </el-form-item>
        </div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="消费者编码" required :error="consumerCodeError">
              <el-input
                v-model="form.consumerCode"
                maxlength="32"
                show-word-limit
                placeholder="最长 32 位"
                @blur="validateConsumerCode"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="用户">
          <RemoteEntitySelect
            v-model="form.userId"
            :load-function="listUsers"
            :label-function="userLabel"
            :selected-options="selectedUserOptions"
            placeholder="请选择用户"
            clearable
            style="width: 100%"
          />
        </el-form-item>
      </div>

      <div v-if="consumer?.secretKey" class="space-line" />

      <div v-if="consumer?.secretKey" class="slot-body consumer-section">
        <div class="section-head">
          <div>
            <h3>密钥信息</h3>
            <p>API Key 用于消费者路由鉴权，重置后旧 Key 会失效。</p>
          </div>
        </div>
        <el-form-item label="API Key">
          <el-input :model-value="consumer.secretKey" readonly type="password" show-password>
            <template #append>
              <el-button @click="copySecretKey(consumer.secretKey)">复制</el-button>
            </template>
          </el-input>
        </el-form-item>
      </div>

      <div class="space-line" />

      <div class="slot-body consumer-section">
        <div class="section-head">
          <div>
            <h3>响应设置</h3>
            <p>控制网关是否在模型返回后追加按计费规则计算的消耗明细。</p>
          </div>
        </div>
        <el-form-item label="返回消耗明细">
          <el-switch
            v-model="form.returnUsageDetail"
            inline-prompt
            active-text="返回"
            inactive-text="不返回"
          />
        </el-form-item>
      </div>

      <div class="space-line" />

      <div class="slot-body consumer-section">
        <div class="section-head">
          <div>
            <h3>策略配置</h3>
            <p>消费者维度的频控和额度限制会与应用策略合并，取更严格的非零限制。</p>
          </div>
        </div>
        <el-form-item label="频控策略">
          <KvEditor v-model="form.rateLimitPolicy" value-mode="number" />
        </el-form-item>
        <el-form-item label="额度策略">
          <KvEditor v-model="form.quotaPolicy" value-mode="number" />
        </el-form-item>
      </div>
    </el-form>
    </el-scrollbar>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import KvEditor from "../common/KvEditor.vue";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import {
  emptyQuotaPolicy,
  emptyRateLimitPolicy,
  isEmptyQuotaPolicy,
  isEmptyRateLimitPolicy,
  normalizeQuotaPolicy,
  normalizeRateLimitPolicy
} from "../../utils/trafficPolicy";
import { listUsers } from "../../api/users";
import {
  checkRoutingConsumerCode,
  createRoutingConsumer,
  updateRoutingConsumer
} from "../../api/routing";
import { generateRoutingRuleCode } from "../../utils/routingRule";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  consumer: { type: Object, default: null }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value)
});

const saving = ref(false);
const consumerCodeError = ref("");
const userOptions = ref([]);

const form = reactive({
  id: null,
  consumerCode: "",
  name: "",
  userId: null,
  returnUsageDetail: false,
  enabled: true,
  rateLimitPolicy: emptyRateLimitPolicy(),
  quotaPolicy: emptyQuotaPolicy()
});

const selectedUserOptions = computed(() => {
  if (!form.userId) return [];
  const user = userOptions.value.find((item) => item.id === form.userId);
  return user ? [user] : [{ id: form.userId, realName: props.consumer?.userName }];
});

watch(
  () => props.modelValue,
  (value) => {
    if (value) {
      resetForm(props.consumer);
      loadOptions();
    }
  }
);

watch(
  () => form.consumerCode,
  () => {
    if (consumerCodeError.value) {
      consumerCodeError.value = "";
    }
  }
);

function userLabel(user) {
  const name = user.realName?.trim() || user.username;
  return name === user.username ? name : `${name}（${user.username}）`;
}

async function loadOptions() {
  const usersRes = await listUsers({ pageNum: 1, pageSize: 10 });
  userOptions.value = Array.isArray(usersRes?.list) ? usersRes.list : Array.isArray(usersRes) ? usersRes : [];
}

function resetForm(row = null) {
  form.id = row?.id ?? null;
  form.consumerCode = row?.consumerCode || generateRoutingRuleCode();
  form.name = row?.name || "";
  form.userId = row?.userId ?? null;
  form.returnUsageDetail = row?.returnUsageDetail === true;
  form.enabled = row?.enabled !== false;
  form.rateLimitPolicy = normalizeRateLimitPolicy(row?.rateLimitPolicy);
  form.quotaPolicy = normalizeQuotaPolicy(row?.quotaPolicy);
  consumerCodeError.value = "";
}

function onClosed() {
  form.id = null;
}

async function validateConsumerCode() {
  const code = form.consumerCode?.trim();
  if (!code) {
    consumerCodeError.value = "请填写消费者编码";
    return false;
  }
  try {
    const res = await checkRoutingConsumerCode(code, props.consumer ? form.id : null);
    if (res?.available) {
      consumerCodeError.value = "";
      return true;
    }
    consumerCodeError.value = "消费者编码已存在，请更换";
    return false;
  } catch {
    return false;
  }
}

function buildPayload() {
  const rateLimitPolicy = isEmptyRateLimitPolicy(form.rateLimitPolicy)
    ? null
    : normalizeRateLimitPolicy(form.rateLimitPolicy);
  const quotaPolicy = isEmptyQuotaPolicy(form.quotaPolicy)
    ? null
    : normalizeQuotaPolicy(form.quotaPolicy);
  return {
    consumerCode: form.consumerCode?.trim(),
    name: form.name?.trim(),
    userId: form.userId,
    returnUsageDetail: form.returnUsageDetail,
    enabled: form.enabled,
    rateLimitPolicy,
    quotaPolicy
  };
}

async function submit() {
  if (!form.name?.trim()) {
    ElMessage.warning("请填写名称");
    return;
  }
  if (!(await validateConsumerCode())) {
    return;
  }
  saving.value = true;
  try {
    if (props.consumer) {
      await updateRoutingConsumer(form.id, buildPayload());
      ElMessage.success("已保存");
    } else {
      await createRoutingConsumer(buildPayload());
      ElMessage.success("已创建");
    }
    visible.value = false;
    emit("saved");
  } finally {
    saving.value = false;
  }
}

async function copySecretKey(secretKey) {
  if (!secretKey) return;
  try {
    await navigator.clipboard.writeText(secretKey);
    ElMessage.success("已复制 API Key");
  } catch {
    ElMessage.error("复制失败");
  }
}
</script>

<style scoped>
.consumer-form {
  padding-bottom: 8px;
}

.consumer-section {
  border-color: #e4e7ed;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.section-head h3 {
  margin: 0 0 4px;
  color: var(--knot-text, #303133);
  font-size: 15px;
  font-weight: 600;
}

.section-head p {
  margin: 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.inline-switch {
  flex: 0 0 auto;
  margin-bottom: 0;
}

.space-line {
  height: 14px;
}
</style>
