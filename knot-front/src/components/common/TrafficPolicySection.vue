<template>
  <div class="traffic-policy">
    <div v-if="title || description || $slots.extra" class="traffic-policy__head">
      <div>
        <h3 v-if="title">{{ title }}</h3>
        <p v-if="description">{{ description }}</p>
      </div>
      <slot name="extra"/>
    </div>

    <!-- 限流：模型 / 路由规则层 -->
    <el-row v-if="mode === 'rate'" :gutter="16">
      <el-col :span="12">
        <el-form-item label="每分钟请求数（RPM）">
          <el-input-number v-model="rateDraft.rpm" :min="0" :step="10" controls-position="right" class="traffic-policy__field"/>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="每分钟 Token（TPM）">
          <el-input-number v-model="rateDraft.tpm" :min="0" :step="1000" controls-position="right" class="traffic-policy__field"/>
        </el-form-item>
      </el-col>
    </el-row>

    <!-- 限额：应用 / 供应商账户 / 消费者层 -->
    <template v-else>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="统计窗口">
            <el-select v-model="quotaDraft.window" class="traffic-policy__field">
              <el-option v-for="item in windowOptions" :key="item.value" :label="item.label" :value="item.value"/>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="最大 Token 数">
            <el-input-number v-model="quotaDraft.maxTokens" :min="0" :step="10000" controls-position="right" class="traffic-policy__field"/>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="成本上限">
            <el-input-number v-model="quotaDraft.costLimit" :min="0" :precision="4" :step="1" controls-position="right" class="traffic-policy__field"/>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="币种">
        <el-select v-model="quotaDraft.currency" class="traffic-policy__field">
          <el-option v-for="item in currencyOptions" :key="item.value" :label="item.label" :value="item.value"/>
        </el-select>
      </el-form-item>
    </template>

    <p class="traffic-policy__hint">{{ mode === 'rate' ? rateHint : quotaHint }}</p>
  </div>
</template>

<script setup lang="ts">
import {computed, reactive, watch} from "vue";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {
  emptyQuotaPolicy,
  emptyRateLimitPolicy,
  normalizeQuotaPolicy,
  normalizeRateLimitPolicy,
  type QuotaPolicy,
  type RateLimitPolicy
} from "@/utils/trafficPolicy";

const props = withDefaults(defineProps<{
  /** rate = 限流（模型 / 路由规则）；quota = 限额（应用 / 供应商账户 / 消费者） */
  mode: "rate" | "quota";
  title?: string;
  description?: string;
  rateLimit?: RateLimitPolicy;
  quota?: QuotaPolicy;
}>(), {
  title: "",
  description: "",
  rateLimit: undefined,
  quota: undefined
});

const emit = defineEmits<{
  (e: "update:rateLimit", value: RateLimitPolicy): void;
  (e: "update:quota", value: QuotaPolicy): void;
}>();

const rateHint = "RPM 在请求进入时计数，超限立即拒绝；TPM 按请求成功后的真实用量累加，超限在下一个请求拦下。";
const quotaHint = "窗口结束自动清零。成本不做汇率换算：只有计费结果币种与所选币种一致时才累计。";

const {optionsOf} = useEnumOptions();
const currencyOptions = computed(() => optionsOf("CurrencyCodeEnum"));
const windowOptions = computed(() => optionsOf("QuotaWindowEnum"));

/** 本地草稿 + 深度 watch 回写父级，不直接改 props 对象 */
const rateDraft = reactive<RateLimitPolicy>({...emptyRateLimitPolicy(), ...props.rateLimit});
const quotaDraft = reactive<QuotaPolicy>({...emptyQuotaPolicy(), ...normalizeQuotaPolicy(props.quota)});

watch(() => props.rateLimit,
  value => Object.assign(rateDraft, normalizeRateLimitPolicy(value)),
  {immediate: true, deep: true});
watch(() => props.quota,
  value => Object.assign(quotaDraft, normalizeQuotaPolicy(value)),
  {immediate: true, deep: true});

watch(rateDraft,
  value => emit("update:rateLimit", {...value}),
  {deep: true});
watch(quotaDraft,
  value => emit("update:quota", {...value}),
  {deep: true});
</script>

<style scoped>
.traffic-policy {
  display: block;
}

.traffic-policy__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.traffic-policy__head h3 {
  margin: 0 0 4px;
  color: var(--knot-text, #303133);
  font-size: 15px;
  font-weight: 600;
}

.traffic-policy__head p {
  margin: 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.traffic-policy__field {
  width: 100%;
}

.traffic-policy__hint {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}
</style>
