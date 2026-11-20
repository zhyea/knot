<template>
  <el-row :gutter="16">
    <el-col :xs="24" :span="9">
      <el-form-item :label="`默认${unitLabel}`">
        <el-input-number
          v-model="form.unitPrice"
          :min="0"
          :step="UNIT_PRICE_STEP"
          controls-position="right"
          style="width: 100%"
        />
      </el-form-item>
    </el-col>
    <el-col :xs="24" :span="15">
      <el-form-item label="口径">
        <span class="image-config__readonly">{{ volumeHint }}</span>
      </el-form-item>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {PricingPlan} from "@/constants/status";
import {UNIT_PRICE_STEP, unitLabelOf} from "@/utils/billingPrice";

/**
 * IMAGE 模式基础价：只有默认单价。
 *
 * <p>出图张数是本模式唯一的计费量（一次请求出几张图），像素只作分档键。
 * 像素档位表归「维度分档价」方案组件托管，这里不渲染——固定价语义是「一个单价直出」。</p>
 */
const props = defineProps({
  form: {type: Object, required: true}
});

const unitLabel = computed(() => {
  const unit = (props.form as Record<string, unknown>).unit;
  return unitLabelOf(typeof unit === "string" ? unit : null, "每张");
});

/** 当前方案下这个单价的实际作用，避免配置者以为它被档位表覆盖或反之 */
const volumeHint = computed(() => {
  const plan = String((props.form as Record<string, unknown>).pricingPlan || "").toUpperCase();
  return plan === PricingPlan.DIMENSION_TIERED
    ? "像素档位价在下方「维度分档价」里配置；本单价只用于上游未回传输出尺寸的请求。"
    : "按出图张数 × 本单价计费；需要按输出像素分档时，把进阶方案切到「维度分档价」。";
});
</script>

<style scoped>
.image-config__readonly {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 20px;
}
</style>
