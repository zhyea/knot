<template>
  <div class="retry-policy">
    <div v-if="title || description" class="retry-policy__head">
      <div>
        <h3 v-if="title">{{ title }}</h3>
        <p v-if="description">{{ description }}</p>
      </div>
      <el-form-item label="启用" class="retry-policy__switch">
        <el-switch v-model="draft.enabled"/>
      </el-form-item>
    </div>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="总尝试次数" label-width="110px">
          <el-input-number
            v-model="draft.maxAttempts"
            :min="1"
            :max="RETRY_MAX_ATTEMPTS_LIMIT"
            :disabled="!draft.enabled"
            class="retry-policy__field"
          />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="退避基数(ms)" label-width="110px">
          <el-input-number
            v-model="draft.backoffBaseMs"
            :min="0"
            :max="RETRY_BACKOFF_LIMIT_MS"
            :step="100"
            :disabled="!draft.enabled"
            class="retry-policy__field"
          />
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="退避上限(ms)" label-width="110px">
          <el-input-number
            v-model="draft.backoffMaxMs"
            :min="0"
            :max="RETRY_BACKOFF_LIMIT_MS"
            :step="500"
            :disabled="!draft.enabled"
            class="retry-policy__field"
          />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="退避倍数" label-width="110px">
          <el-input-number
            v-model="draft.multiplier"
            :min="1"
            :max="RETRY_MULTIPLIER_LIMIT"
            :step="0.5"
            :precision="1"
            :disabled="!draft.enabled"
            class="retry-policy__field"
          />
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="判定方式" label-width="110px">
          <el-select v-model="draft.retryOnMode" :disabled="!draft.enabled" class="retry-policy__field">
            <el-option label="白名单（命中才重试）" :value="RETRY_MODE_ALLOWLIST"/>
            <el-option label="黑名单（命中才不重试）" :value="RETRY_MODE_DENYLIST"/>
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="状态码" label-width="110px">
          <el-select
            v-model="draft.retryOn"
            multiple
            filterable
            allow-create
            default-first-option
            :disabled="!draft.enabled"
            placeholder="如 500 / 502 / 429"
            class="retry-policy__field"
          >
            <el-option v-for="item in statusOptions" :key="item" :label="item" :value="item"/>
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="退避抖动" label-width="110px">
          <el-switch v-model="draft.jitter" :disabled="!draft.enabled"/>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="服从 Retry-After" label-width="110px">
          <el-switch v-model="draft.respectRetryAfter" :disabled="!draft.enabled"/>
        </el-form-item>
      </el-col>
    </el-row>

    <p class="retry-policy__hint">{{ hint }}</p>
  </div>
</template>

<script setup lang="ts">
import {computed, reactive, watch} from "vue";
import {
  defaultRetryPolicy,
  normalizeRetryPolicy,
  RETRY_BACKOFF_LIMIT_MS,
  RETRY_MAX_ATTEMPTS_LIMIT,
  RETRY_MODE_ALLOWLIST,
  RETRY_MODE_DENYLIST,
  RETRY_MULTIPLIER_LIMIT,
  type RetryPolicy
} from "@/utils/retryPolicy";

const props = withDefaults(defineProps<{
  title?: string;
  description?: string;
  retryPolicy?: RetryPolicy;
}>(), {
  title: "",
  description: "",
  retryPolicy: undefined
});

const emit = defineEmits<{
  (e: "update:retryPolicy", value: RetryPolicy): void;
}>();

const hint =
  "重试发生在「切换下一个候选」之前：同一目标连续失败先在原地重投，次数耗尽才 failover。"
  + "默认重试 5xx 与 429 等瞬态失败；4xx 与配置类错误不重试，直接切换目标。"
  + "流式请求只在首个字节之前失败才可重试，一旦开始转发即向调用方提交。";

/** 常用状态码预设，仍可手动输入任意状态码 */
const statusOptions = computed(() => ["429", "500", "502", "503", "504", "408", "409"]);

/** 本地草稿 + 深度 watch 回写父级，不直接改 props 对象 */
const draft = reactive<RetryPolicy>({...defaultRetryPolicy(), ...normalizeRetryPolicy(props.retryPolicy)});

watch(() => props.retryPolicy,
  value => Object.assign(draft, normalizeRetryPolicy(value)),
  {immediate: true, deep: true});

watch(draft,
  value => emit("update:retryPolicy", {...value}),
  {deep: true});
</script>

<style scoped>
.retry-policy {
  display: block;
}

.retry-policy__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.retry-policy__head h3 {
  margin: 0 0 4px;
  color: var(--knot-text, #303133);
  font-size: 15px;
  font-weight: 600;
}

.retry-policy__head p {
  margin: 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.retry-policy__switch {
  flex: 0 0 auto;
  margin-bottom: 0;
}

.retry-policy__field {
  width: 100%;
}

.retry-policy__hint {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}
</style>
