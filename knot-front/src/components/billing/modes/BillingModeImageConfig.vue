<template>
  <template v-if="tokenUnit">
    <el-row :gutter="16">
      <el-col :xs="24" :span="12">
        <el-form-item :label="文本输入">
          <el-input-number
            v-model="form.imageTokenInputText"
            :min="0"
            :step="UNIT_PRICE_STEP"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :span="12">
        <el-form-item :label="图像输入">
          <el-input-number
            v-model="form.imageTokenInputImage"
            :min="0"
            :step="UNIT_PRICE_STEP"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :span="12">
        <el-form-item :label="图像输出">
          <el-input-number
            v-model="form.imageTokenOutputImage"
            :min="0"
            :step="UNIT_PRICE_STEP"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :span="12">
        <el-form-item :label="文本缓存输入">
          <el-input-number
            v-model="form.imageTokenCacheReadText"
            :min="0"
            :step="UNIT_PRICE_STEP"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :span="12">
        <el-form-item :label="图像缓存输入">
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
  <template v-else>
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
    <!-- 五个模态单价只在 token 量纲下渲染，这里给出入口，避免「只看到一个默认单价」就以为不支持模态定价 -->
    <div class="image-config__token-hint">
      <template v-if="tokenUnitCode">
        <span>
          上游按 Token 计费（文本输入 / 图像输入 / 文本缓存输入 / 图像缓存输入 / 图像输出分别定价）时，
          把「计费单位」切到 {{ tokenUnitLabel }} 即可分别填写五个模态单价。
        </span>
        <el-link type="primary" @click="switchToTokenUnit">切换为{{ tokenUnitLabel }}</el-link>
      </template>
      <span v-else class="image-config__token-hint--muted">当前能力矩阵未提供图片的 Token 计量单位，只能按张计价。</span>
    </div>
  </template>
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
 * 张数与像素都不参与取价。五价只在 token 量纲下渲染，按张时给出一键切换入口。</p>
 */
const props = defineProps({
  form: {type: Object, required: true},
  /** 当前能力矩阵里图片可用的 token 单位（如 {@code 1M_TOKENS}）；空串表示后端未开放，只能按张 */
  tokenUnitCode: {type: String, default: ""}
});

const tokenUnit = computed(() => {
  const unit = (props.form as Record<string, unknown>).unit;
  return isTokenPricedUnit(typeof unit === "string" ? unit : null);
});

const unitLabel = computed(() => {
  const unit = (props.form as Record<string, unknown>).unit;
  return unitLabelOf(typeof unit === "string" ? unit : null, "每张");
});

/** 提示文案里的目标单位名（token 量纲下与 unitLabel 同源） */
const tokenUnitLabel = computed(() => unitLabelOf(props.tokenUnitCode || null, "每百万 Token"));

/** 切到 token 量纲：单位由「计费单位」下拉唯一决定，这里只是替用户点一下 */
function switchToTokenUnit() {
  if (!props.tokenUnitCode) {
    return;
  }
  (props.form as Record<string, unknown>).unit = props.tokenUnitCode;
}

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

.image-config__token-hint {
  display: flex;
  gap: 8px;
  align-items: baseline;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 20px;
  margin-top: 4px;
}

.image-config__token-hint--muted {
  color: var(--el-text-color-placeholder);
}
</style>
