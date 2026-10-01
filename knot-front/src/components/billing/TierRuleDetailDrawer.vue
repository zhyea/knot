<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    size="60%"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="!rows.length">
      <el-empty description="该规则未配置阶梯档位"/>
    </template>
    <template v-else>
      <el-descriptions border size="small" :column="2" class="tier-detail__meta">
        <el-descriptions-item label="供应商">{{ rule?.providerName || "全局" }}</el-descriptions-item>
        <el-descriptions-item label="模型族">{{ rule?.modelFamilyName || "默认" }}</el-descriptions-item>
        <el-descriptions-item label="计费单位">{{ rule?.unit || "-" }}</el-descriptions-item>
        <el-descriptions-item label="币种">{{ rule?.currency || "-" }}</el-descriptions-item>
      </el-descriptions>

      <div class="tier-detail__hint">
        按{{ scope.amountLabel }}命中唯一档位计价，不跨档拆段；未命中档位回退基础单价。区间为闭区间，末档可无限延伸。
      </div>

      <el-table :data="rows" border size="small" stripe>
        <el-table-column label="档位" width="72" align="center">
          <template #default="{ $index }">第 {{ $index + 1 }} 档</template>
        </el-table-column>
        <el-table-column label="用量区间" min-width="180">
          <template #default="{ row }">{{ describeTierRange(row) }}</template>
        </el-table-column>
        <el-table-column
          v-for="field in TIER_PRICE_FIELDS"
          :key="field.key"
          :label="field.label"
          min-width="110"
          align="right"
        >
          <template #default="{ row }">{{ formatAmount(row.unitPrices?.[field.key]) }}</template>
        </el-table-column>
      </el-table>

      <div class="tier-detail__base">
        <span class="tier-detail__base-title">基础单价（兜底）</span>
        <el-descriptions border size="small" :column="3">
          <el-descriptions-item
            v-for="field in TIER_PRICE_FIELDS"
            :key="field.key"
            :label="field.label"
          >
            {{ formatAmount(basePrices[field.key]) }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import {computed} from "vue";
import type {Dict, Row} from "@/types";
import {TIER_PRICE_FIELDS, describeTierRange, formatAmount, parseTierRows, tierScope, toNumberOrNull} from "@/utils/billingTier";
import type {TierPriceSet} from "@/utils/billingTier";
import {parseJsonObject} from "@/utils/format";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  /** 阶梯所属的计费规则（当前版本行） */
  rule: {type: Object as () => Row | null, default: null}
});

const emit = defineEmits(["update:modelValue"]);

const title = computed(() => `阶梯明细 - ${props.rule?.code || `#${props.rule?.id ?? ""}`}`);
const rows = computed(() => parseTierRows(parseJsonObject(props.rule?.configJson).tier));
/** 用量口径由计费模式决定 */
const scope = computed(() => tierScope(props.rule?.billingMode));
const basePrices = computed<TierPriceSet>(() => {
  const config: Dict = parseJsonObject(props.rule?.configJson);
  const base: Dict = (config.basePrices && typeof config.basePrices === "object" ? config.basePrices : {});
  return {
    input: toNumberOrNull(base.input ?? config.defaultUnitPrice),
    output: toNumberOrNull(base.output ?? config.defaultUnitPrice),
    cacheRead: toNumberOrNull(base.cacheRead),
    cacheWrite: toNumberOrNull(base.cacheWrite),
    cacheWrite5m: toNumberOrNull(base.cacheWrite5m),
    cacheWrite1h: toNumberOrNull(base.cacheWrite1h)
  };
});
</script>

<style scoped>
.tier-detail__meta {
  margin-bottom: 12px;
}

.tier-detail__hint {
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 20px;
}

.tier-detail__base {
  margin-top: 16px;
}

.tier-detail__base-title {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
}
</style>
