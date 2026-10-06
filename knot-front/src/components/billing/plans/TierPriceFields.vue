<template>
  <el-row :gutter="16">
    <el-col v-for="field in visiblePriceFields" :key="field.key" :span="12">
      <el-form-item
        :label="field.label"
        :error="fieldError(field.key)"
        class="tier-price-field"
      >
        <el-input-number
          :model-value="prices[field.key]"
          :min="0"
          :step="UNIT_PRICE_STEP"
          :precision="UNIT_PRICE_PRECISION"
          placeholder="必填"
          class="tier-price-field__input"
          @update:model-value="(value) => update(field.key, value)"
        />
      </el-form-item>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import {computed, type PropType} from "vue";
import {TIER_PRICE_FIELDS, issueMessage, toNumberOrNull} from "@/utils/billingTier";
import {UNIT_PRICE_PRECISION, UNIT_PRICE_STEP} from "@/utils/billingPrice";
import type {TierIssue, TierPriceSet} from "@/utils/billingTier";

const props = defineProps({
  /** 所属档位序号（-1 表示与档位无关） */
  index: {type: Number, default: -1},
  prices: {type: Object, required: true},
  cacheWriteMode: {type: String as () => "standard" | "ttl", default: "standard"},
  issues: {type: Array as PropType<TierIssue[]>, default: (): TierIssue[] => []}
});

const emit = defineEmits(["update:prices"]);

const priceSet = computed(() => props.prices as TierPriceSet);
const visiblePriceFields = computed(() => TIER_PRICE_FIELDS.filter((field) => {
  if (props.cacheWriteMode === "standard") {
    return field.key !== "cacheWrite5m" && field.key !== "cacheWrite1h";
  }
  return field.key !== "cacheWrite";
}));

function update(key: keyof TierPriceSet, value: number | undefined | null) {
  emit("update:prices", {...priceSet.value, [key]: toNumberOrNull(value)});
}

function fieldError(key: keyof TierPriceSet): string {
  return issueMessage(props.issues, props.index, key);
}
</script>

<style scoped>
.tier-price-field__input {
  width: 100%;
}

</style>
