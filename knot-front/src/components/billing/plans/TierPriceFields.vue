<template>
  <el-row :gutter="12">
    <el-col v-for="field in TIER_PRICE_FIELDS" :key="field.key" :span="8">
      <el-form-item
        :label="field.label"
        :error="fieldError(field.key)"
        label-position="top"
        class="tier-price-field"
      >
        <el-input-number
          :model-value="prices[field.key]"
          :min="0"
          :step="0.0001"
          :precision="6"
          :controls="false"
          size="small"
          placeholder="必填"
          class="tier-price-field__input"
          @update:model-value="(value) => update(field.key, value)"
        />
      </el-form-item>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {TIER_PRICE_FIELDS, issueMessage, toNumberOrNull} from "@/utils/billingTier";
import type {TierIssue, TierPriceSet} from "@/utils/billingTier";

const props = defineProps({
  /** 所属档位序号（-1 表示与档位无关） */
  index: {type: Number, default: -1},
  prices: {type: Object, required: true},
  issues: {type: Array as () => TierIssue[], default: (): TierIssue[] => []}
});

const emit = defineEmits(["update:prices"]);

const priceSet = computed(() => props.prices as TierPriceSet);

function update(key: keyof TierPriceSet, value: number | undefined | null) {
  emit("update:prices", {...priceSet.value, [key]: toNumberOrNull(value)});
}

function fieldError(key: keyof TierPriceSet): string {
  return issueMessage(props.issues, props.index, key);
}
</script>

<style scoped>
.tier-price-field {
  margin-bottom: 12px;
}

.tier-price-field__input {
  width: 100%;
}
</style>
