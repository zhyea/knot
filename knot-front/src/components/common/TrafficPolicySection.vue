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
        <el-form-item label="每分钟请求数（RPM）" label-width="150px">
          <el-input-number v-model="rateDraft.rpm" :min="0" :step="10" controls-position="right"
                           class="traffic-policy__field"/>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="每分钟 Token（TPM）" label-width="150px">
          <el-input-number v-model="rateDraft.tpm" :min="0" :step="1000" controls-position="right"
                           class="traffic-policy__field"/>
        </el-form-item>
      </el-col>
    </el-row>

    <!-- 限额：应用 / 供应商账户 / 消费者层（多窗口卡列表） -->
    <template v-else>
      <div v-for="(_, index) in quotaList" :key="index" class="quota-card">
        <div class="quota-card__head">
          <span class="quota-card__title">窗口 {{ index + 1 }}</span>
          <el-button text type="danger" :icon="Delete" @click="removeQuota(index)">删除</el-button>
        </div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="统计窗口">
              <el-select v-model="quotaList[index].window" class="traffic-policy__field">
                <el-option v-for="item in windowOptions" :key="item.value" :label="item.label" :value="item.value"/>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="币种">
              <el-select v-model="quotaList[index].currency" class="traffic-policy__field">
                <el-option v-for="item in currencyOptions" :key="item.value" :label="item.label" :value="item.value"/>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="最大Token数">
              <el-input-number v-model="quotaList[index].maxTokens" controls-position="right"
                               :min="0" :step="10000" class="traffic-policy__field"/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="成本上限">
              <el-input-number v-model="quotaList[index].costLimit" controls-position="right"
                               :min="0" :precision="4" :step="1" class="traffic-policy__field"/>
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <el-button :icon="Plus" plain class="quota-add" @click="addQuota">新增窗口配额</el-button>

      <p v-if="quotaList.length === 0" class="traffic-policy__empty">
        尚未配置窗口限额，点击上方按钮新增（可叠加多个不同统计窗口）。
      </p>
    </template>

    <p class="traffic-policy__hint">{{ mode === 'rate' ? rateHint : quotaHint }}</p>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {Delete, Plus} from "@element-plus/icons-vue";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {useDraftModel} from "@/composables/useDraftModel";
import {
  emptyQuotaPolicy,
  normalizeQuotaPolicies,
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
  quotas?: QuotaPolicy[];
}>(), {
  title: "",
  description: "",
  rateLimit: undefined,
  quotas: undefined
});

const emit = defineEmits<{
  (e: "update:rateLimit", value: RateLimitPolicy): void;
  (e: "update:quotas", value: QuotaPolicy[]): void;
}>();

const rateHint = "RPM 在请求进入时计数，超限立即拒绝；TPM 按请求成功后的真实用量累加，超限在下一个请求拦下。";
const quotaHint = "每个统计窗口独立清零。成本不做汇率换算：只有计费结果币种与所选币种一致时才累计。";

const {optionsOf} = useEnumOptions();
const currencyOptions = computed(() => optionsOf("CurrencyCodeEnum"));
const windowOptions = computed(() => optionsOf("QuotaWindowEnum"));

/** 本地草稿 + 双向同步：不直接改 props 对象，回环由 useDraftModel 内的结构比较阻断 */
const rateDraft = useDraftModel<RateLimitPolicy>(
  () => props.rateLimit,
  normalizeRateLimitPolicy,
  value => emit("update:rateLimit", value)
);
const quotaList = useDraftModel<QuotaPolicy[]>(
  () => props.quotas,
  normalizeQuotaPolicies,
  value => emit("update:quotas", value)
);

function addQuota() {
  quotaList.push(emptyQuotaPolicy());
}

function removeQuota(index: number) {
  quotaList.splice(index, 1);
}
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

.quota-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 14px 0;
  margin-bottom: 12px;
  background: #fafafa;
}

.quota-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.quota-card__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--knot-text, #303133);
}

.quota-add {
  width: 100%;
}

.traffic-policy__empty {
  margin: 4px 0 0;
  color: #c0c4cc;
  font-size: 12px;
}
</style>
