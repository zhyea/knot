<template>
  <template v-if="tokenUnit">
    <el-row :gutter="16">
      <el-col :xs="24" :span="8">
        <el-form-item :label="`文本输入 · ${unitLabel}`">
          <el-input-number
            v-model="form.imageTokenInputText"
            :min="0"
            :step="UNIT_PRICE_STEP"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :span="8">
        <el-form-item :label="`图像输入 · ${unitLabel}`">
          <el-input-number
            v-model="form.imageTokenInputImage"
            :min="0"
            :step="UNIT_PRICE_STEP"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :span="8">
        <el-form-item :label="`图像输出 · ${unitLabel}`">
          <el-input-number
            v-model="form.imageTokenOutputImage"
            :min="0"
            :step="UNIT_PRICE_STEP"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :span="8">
        <el-form-item :label="`文本缓存输入 · ${unitLabel}`">
          <el-input-number
            v-model="form.imageTokenCacheReadText"
            :min="0"
            :step="UNIT_PRICE_STEP"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :span="8">
        <el-form-item :label="`图像缓存输入 · ${unitLabel}`">
          <el-input-number
            v-model="form.imageTokenCacheReadImage"
            :min="0"
            :step="UNIT_PRICE_STEP"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :span="16">
        <el-form-item label="口径">
          <span class="image-config__readonly">{{ volumeHint }}</span>
        </el-form-item>
      </el-col>
    </el-row>
  </template>
  <el-row v-else :gutter="16">
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
import {UNIT_PRICE_STEP, isTokenPricedUnit, unitLabelOf} from "@/utils/billingPrice";

/**
 * IMAGE 模式基础价，两套量纲由计费单位决定。
 *
 * <p>按张：只有默认单价。出图张数是计费量，像素只作分档键；
 * 像素档位表归「维度分档价」方案组件托管，这里不渲染——固定价语义是「一个单价直出」。</p>
 *
 * <p>按 token：文本输入 / 图像输入 / 图像输出各一个模态单价，缓存输入两条可留空（留空按同模态输入价计）。
 * 张数与像素都不参与取价。</p>
 */
const props = defineProps({
  form: {type: Object, required: true}
});

const tokenUnit = computed(() => {
  const unit = (props.form as Record<string, unknown>).unit;
  return isTokenPricedUnit(typeof unit === "string" ? unit : null);
});

const unitLabel = computed(() => {
  const unit = (props.form as Record<string, unknown>).unit;
  return unitLabelOf(typeof unit === "string" ? unit : null, "每张");
});

/** 当前量纲下这些单价的实际作用，避免配置者以为它被档位表覆盖或反之 */
const volumeHint = computed(() => {
  if (tokenUnit.value) {
    return "按 token 计价：文本与图像输入分别取模态单价，图像输出单独计价；缓存输入留空按同模态输入价计。";
  }
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
