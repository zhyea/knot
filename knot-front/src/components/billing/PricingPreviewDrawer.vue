<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    size="46%"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="preview__form">
      <el-form-item label="发生时刻（UTC）" label-width="130px">
        <el-date-picker
          v-model="probeDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="日期"
          size="small"
          class="preview__date"
        />
        <el-time-picker
          v-model="probeTime"
          format="HH:mm"
          value-format="HH:mm"
          placeholder="时刻"
          size="small"
          class="preview__time"
        />
      </el-form-item>
      <el-form-item label="用量" label-width="130px">
        <el-input-number
          v-model="usageAmount"
          :min="0"
          :step="100"
          :controls="false"
          size="small"
          class="preview__amount"
        />
        <span class="preview__hint">阶梯方案按此用量选档；其余方案不影响结果</span>
      </el-form-item>
      <el-form-item label-width="130px">
        <el-button type="primary" size="small" :loading="loading" @click="run">试算</el-button>
        <span class="preview__hint">走网关同一个判定器与价格解析链，所见即真实计费</span>
      </el-form-item>
    </div>

    <el-divider/>

    <template v-if="result">
      <el-descriptions border size="small" :column="2">
        <el-descriptions-item label="规则">{{ result.ruleCode || `#${result.ruleId}` }}</el-descriptions-item>
        <el-descriptions-item label="计费模式">{{ modeLabel(result.billingMode) }}</el-descriptions-item>
        <el-descriptions-item label="进阶方案">{{ planLabel(result.pricingPlan) }}</el-descriptions-item>
        <el-descriptions-item label="币种 / 单位">
          {{ result.currency || "-" }} / {{ result.unit || "-" }}（{{ result.unitSize }}）
        </el-descriptions-item>
        <el-descriptions-item label="判定时刻">{{ result.occurredAt || "-" }}</el-descriptions-item>
        <el-descriptions-item label="时区">{{ result.timezone || "不适用" }}</el-descriptions-item>
      </el-descriptions>

      <div class="preview__phase">
        <el-tag v-if="result.phase" :type="result.phase === 'PEAK' ? 'warning' : 'success'" size="small">
          {{ PEAK_PHASE_LABELS[result.phase] || result.phase }} × {{ result.multiplier }}
        </el-tag>
        <el-tag v-else type="info" size="small">{{ planLabel(result.pricingPlan) }} 不随时间变化</el-tag>
        <span class="preview__hint">{{ reasonText }}</span>
      </div>

      <el-table :data="priceRows" border size="small" stripe class="preview__prices">
        <el-table-column prop="label" label="价格种类" min-width="130"/>
        <el-table-column label="最终单价" min-width="130" align="right">
          <template #default="{ row }">{{ row.price }}</template>
        </el-table-column>
      </el-table>
    </template>

    <el-empty v-else description="填入时间后点「试算」"/>
  </el-drawer>
</template>

<script setup lang="ts">
import {computed, ref} from "vue";
import {ElMessage} from "element-plus";
import type {Dict, Row} from "@/types";
import {previewBillingRule} from "@/api/billing";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {PEAK_PHASE_LABELS, PEAK_REASONS} from "@/utils/billingPeakOffPeak";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  rule: {type: Object as () => Row | null, default: null}
});

const emit = defineEmits(["update:modelValue"]);

const {labelOf: enumLabelOf} = useEnumOptions();

const title = computed(() => `方案试算 - ${props.rule?.code || `#${props.rule?.id ?? ""}`}`);
const probeDate = ref<string>(new Date().toISOString().slice(0, 10));
const probeTime = ref<string>("02:00");
const usageAmount = ref<number>(1000);
const loading = ref(false);
const result = ref<Dict | null>(null);

/** 价格种类键 -> 中文（后端 TOKEN 给四项，其余给 default） */
const PRICE_LABELS: Record<string, string> = {
  input: "输入",
  output: "输出",
  cache_read: "缓存读取",
  cache_write: "缓存写入",
  default: "默认单价"
};

const priceRows = computed(() => {
  const prices = (result.value?.prices || {}) as Dict;
  return Object.keys(prices).map((key) => ({
    key,
    label: PRICE_LABELS[key] || key,
    price: String(prices[key] ?? "-")
  }));
});

const reasonText = computed(() => {
  const reason = result.value?.reason;
  if (!reason) {
    return "";
  }
  return PEAK_REASONS[String(reason)] || String(reason);
});

function modeLabel(code: unknown): string {
  return enumLabelOf("BillingModeEnum", code, String(code || "-"));
}

function planLabel(code: unknown): string {
  return enumLabelOf("PricingPlanEnum", code, String(code || "-"));
}

async function run() {
  if (!props.rule?.id) {
    return;
  }
  const occurredAt = toInstant(probeDate.value, probeTime.value);
  if (!occurredAt) {
    ElMessage.warning("请选择有效的日期与时刻");
    return;
  }
  loading.value = true;
  try {
    result.value = (await previewBillingRule(props.rule.id, {
      occurredAt,
      usageAmount: usageNumber.value
    })) as Dict;
  } finally {
    loading.value = false;
  }
}

const usageNumber = computed(() => {
  const parsed = Number(usageAmount.value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0;
});

/** 日期 + HH:mm 拼成后端可解析的 ISO instant（判定时区由规则配置决定，这里统一按 UTC 送） */
function toInstant(date: string, time: string): string | null {
  if (!date || !time || !/^\d{2}:\d{2}$/.test(time)) {
    return null;
  }
  const parsed = new Date(`${date}T${time}:00Z`);
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString().replace(".000Z", "Z");
}
</script>

<style scoped>
.preview__form {
  padding-top: 4px;
}

.preview__date {
  width: 150px;
  margin-right: 8px;
}

.preview__time {
  width: 132px;
  margin-right: 10px;
}

.preview__amount {
  width: 160px;
  margin-right: 10px;
}

.preview__hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 20px;
}

.preview__phase {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin: 12px 0;
}

.preview__prices {
  margin-top: 4px;
}
</style>
