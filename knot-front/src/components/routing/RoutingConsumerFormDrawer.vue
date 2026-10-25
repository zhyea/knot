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
    <el-form :model="form" label-width="96px" class="consumer-form">
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
              :disabled="codeLocked"
              @blur="validateConsumerCode"
            />
            <div v-if="codeLocked" class="form-tip">{{ codeLockHint }}</div>
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
            v-model="form.userUsername"
            :load-function="loadUserUsernameOptions"
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

      <TrafficPolicySection
        class="slot-body consumer-section"
        mode="quota"
        title="限额配置"
        description="消费者维度的限额会与应用限额合并，取更严格的非零上限；可叠加多个不同统计窗口。"
        v-model:quotas="form.quotaPolicies"
      />
    </el-form>
    </el-scrollbar>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import {type PropType,  computed, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import TrafficPolicySection from "../common/TrafficPolicySection.vue";
import {
  emptyQuotaPolicies,
  isEmptyQuotaPolicy,
  normalizeQuotaPolicy,
  normalizeQuotaPolicies
} from "@/utils/trafficPolicy";
import {listUserUsernameOptions} from "@/api/options";
import {
  checkRoutingConsumerCode,
  createRoutingConsumer,
  updateRoutingConsumer
} from "@/api/routing";
import {toOptionsLoader} from "@/utils/options";
import type {Dict, Row} from "@/types";
import {useMissingOptionGuard} from "@/composables/useMissingOptionGuard";
import {useCodeBindingLock} from "@/composables/useCodeBindingLock";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  consumer: { type: Object as PropType<Dict | null>, default: null }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value)
});

const saving = ref(false);
const consumerCodeError = ref("");
/** 编码引用处数（绑定的路由规则数，后端 ruleCount）：>0 时编码不可改 */
const boundCount = ref(0);
const {codeLocked, codeLockHint} = useCodeBindingLock(() => props.consumer != null, () => boundCount.value);

const form = reactive({
  id: null,
  consumerCode: "",
  name: "",
  userUsername: null,
  returnUsageDetail: false,
  enabled: true,
  quotaPolicies: emptyQuotaPolicies()
});

watch(
  () => props.modelValue,
  (value) => {
    if (value) {
      resetForm(props.consumer);
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

const loadUserUsernameOptions = toOptionsLoader(listUserUsernameOptions);

function resetForm(row: Row | null = null) {
  form.id = row?.id ?? null;
  // 不预置默认消费者编码：编码由用户自行填写（编码即接口与审计定位标识）
  form.consumerCode = row?.consumerCode || "";
  form.name = row?.name || "";
  form.userUsername = row?.userUsername ?? null;
  form.returnUsageDetail = row?.returnUsageDetail === true;
  form.enabled = row?.enabled !== false;
  form.quotaPolicies = normalizeQuotaPolicies(row?.quotaPolicies);
  // 规则数即消费者编码的引用处数：>0 时编码不可改（与后端 CodeBindingSupport 同源）
  boundCount.value = Number(row?.ruleCount ?? 0);
  consumerCodeError.value = "";
}

function onClosed() {
  form.id = null;
}

async function validateConsumerCode() {
  // 编码已锁（被路由规则绑定）时不校验唯一性：字段本就不可编辑
  if (codeLocked.value) {
    consumerCodeError.value = "";
    return true;
  }
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
  const quotaPolicies = form.quotaPolicies
    .map(normalizeQuotaPolicy)
    .filter((q) => !isEmptyQuotaPolicy(q));
  return {
    consumerCode: form.consumerCode?.trim(),
    name: form.name?.trim(),
    userUsername: form.userUsername,
    returnUsageDetail: form.returnUsageDetail,
    enabled: form.enabled,
    quotaPolicies
  };
}

// 下拉缺失值守卫：已选项被删除/无权限时阻止提交（options 契约第 10 条）
const missing = useMissingOptionGuard();

async function submit() {
  if (missing.hasMissing.value) {
    ElMessage.warning(missing.hint.value);
    return;
  }
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
      await updateRoutingConsumer(form.id!, buildPayload());
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

async function copySecretKey(secretKey: string | null | undefined) {
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

.section-head p {
  margin: 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

</style>
