<template>
  <div v-if="showUsage" class="video-usage-config">
    <div class="video-usage-config__title">视频计费量与输入侧分量</div>

    <!-- 合成量口径是「量」的轴：只有存在「含参考」组合（输入秒要折进量）时才有语义，
         否则固定为「仅输出」并隐藏——它与「输入侧分量」表达的是同一件事的两种口径 -->
    <template v-if="showQuantity">
      <el-divider content-position="left">
        <span class="video-usage-config__group">计费量口径</span>
      </el-divider>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="合成量模式">
            <el-select v-model="form.videoQuantityMode" style="width: 100%">
              <el-option v-for="item in QUANTITY_MODES" :key="item.value" :label="item.label" :value="item.value"/>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="口径说明">
            <span class="video-usage-config__readonly">{{ quantityHint }}</span>
          </el-form-item>
        </el-col>
      </el-row>
    </template>

    <el-divider content-position="left">
      <span class="video-usage-config__group">输入侧分量（可选，留空不计费）</span>
    </el-divider>
    <!-- 两行两列：每个分量各自持有 span，调宽只改自己那一格 -->
    <el-row :gutter="16">
      <el-col v-if="showInputVideo" :xs="24" :span="8">
        <el-form-item label="输入视频 每秒">
          <el-input-number v-model="form.videoInputVideoPerSecond"
                           :min="0"
                           :step="UNIT_PRICE_STEP"
                           controls-position="right"
                           style="width: 100%"/>
        </el-form-item>
      </el-col>
      <el-col :xs="24" :span="8">
        <el-form-item label="输入图片 每张">
          <el-input-number v-model="form.videoInputImagePerImage"
                           :min="0"
                           :step="UNIT_PRICE_STEP"
                           controls-position="right"
                           style="width: 100%"/>
        </el-form-item>
      </el-col>
      <el-col :xs="24" :span="8">
        <el-form-item label="图片免费张数">
          <el-input-number v-model="form.videoFreeInputImages"
                           :min="0"
                           :step="1"
                           controls-position="right"
                           style="width: 100%"/>
        </el-form-item>
      </el-col>
      <el-col v-if="showAudioSurcharge" :xs="24" :span="8">
        <el-form-item label="音频直出附加费">
          <el-input-number v-model="form.videoAudioSurcharge"
                           :min="0"
                           :step="UNIT_PRICE_STEP"
                           controls-position="right"
                           style="width: 100%"/>
        </el-form-item>
      </el-col>
    </el-row>
    <el-alert type="info" :closable="false" class="video-usage-config__hint">
      <template #title>
        <span>
          输入侧分量按「输出秒 + 输入秒 + 输入图片」三路各自计价后求和；
          分量单价独立于上方分辨率档，不会被默认单价顶替。附加费是与用量无关的常数项（如 Vidu 直出音频 +15）。
          它与「有声单独定价」是同一件事的两种表达：勾选「有声单独定价」后音频附加费不再出现，已填值会被清空——同一项不收两遍。
        </span>
      </template>
    </el-alert>
  </div>
</template>

<script setup lang="ts">
import {computed, watch} from "vue";
import {ElMessage} from "element-plus";
import {PricingPlan} from "@/constants/status";
import {UNIT_PRICE_STEP, isTokenPricedUnit} from "@/utils/billingPrice";
import {hasAudioPriceTier, normalizeCombos} from "@/utils/billingResolution";
import type {ResolutionRow} from "@/utils/billingResolution";

const props = defineProps({
  form: {type: Object, required: true}
});

/**
 * 由<b>渲染档位表的那一方</b>挂载：FIXED/TIERED/PEAK_OFF_PEAK 在视频模式组件末尾，
 * DIMENSION_TIERED 在方案组件（{@link PricingPlanDimensionTieredConfig}）的档位表下方。
 * 模式本身由调用方决定，这里只判「这份量由谁托管」：token 量纲与 MiniMax 方案各有归属，本区块让位。
 */
const tokenPriced = computed(() => isTokenPricedUnit(props.form.unit));
const isVendorMiniMax = computed(
  () => String(props.form.pricingPlan || "").toUpperCase() === PricingPlan.VENDOR_MINIMAX
);

const showUsage = computed(() => !tokenPriced.value && !isVendorMiniMax.value);

const isFixedPlan = computed(() => {
  const plan = String(props.form.pricingPlan || "").toUpperCase();
  return plan === "" || plan === PricingPlan.FIXED;
});

const combos = computed(() => normalizeCombos(props.form.videoDimensionCombos));

/**
 * 档位表里是否真的填了有声价（{@code :AUDIO} / {@code :REFERENCE:AUDIO} 任一有值）。
 *
 * <p>判「有没有真的按有声收过钱」只能看值，不能看列开没开：老配置没有
 * {@code video.dimensionCombos} 字段时界面按四列全开回显（{@code LEGACY_COMBOS}），
 * 那两列可能一个价都没填，此时附加费就是唯一的收费项，清掉就是静默少收。</p>
 */
const hasAudioTierPrice = computed(() =>
  (Array.isArray(props.form.videoResolutionRows) ? props.form.videoResolutionRows as ResolutionRow[] : [])
    .some((row) => row.audioPrice != null || row.referenceAudioPrice != null)
);

/**
 * 有声是否已按单价计费（{@code :AUDIO} / {@code :REFERENCE:AUDIO} 任一存在）。
 *
 * <p>此时音频直出附加费不能再用：后端把附加费在分量求和之后 add，
 * 与维度键的有声价是两笔钱，同时存在就是同一件事收两遍。</p>
 *
 * <p>⭐ 两根判据缺一不可：组合轴必须是用户<b>显式声明</b>（历史口径回显不算声明，见
 * {@code videoCombosLegacy}），且档位表里<b>真有</b>有声价。只看组合轴会把
 * 「四列全开但只填了基础价 + 附加费」的存量配置判成双收，打开抽屉就静默清掉一个真实收费项。</p>
 */
const audioPriced = computed(() =>
  hasAudioPriceTier(props.form.videoDimensionCombos)
  && (!props.form.videoCombosLegacy || hasAudioTierPrice.value)
);

/**
 * 是否含「含参考」组合：Kling 4.0 那类「有输入视频 → 量=输出×2」的价表靠它触发。
 * 固定价没有档位表，参考视频不改变单价，因此同样不展示合成量口径。
 */
const showQuantity = computed(
  () => !isFixedPlan.value
    && (combos.value.includes("REFERENCE") || combos.value.includes("REFERENCE_AUDIO"))
);

/**
 * 输入秒是否已被计费量口径折进输出量。
 *
 * <p>折进去之后再按 {@code 输入视频 每秒} 单独收一次，同一批秒会收两遍；
 * 后端 {@code BillingConfig#validateDimensionTiered} 同口径拒存，这里直接不给填。</p>
 */
const inputVideoFolded = computed(
  () => String(props.form.pricingPlan || "").toUpperCase() === PricingPlan.DIMENSION_TIERED
    && String(props.form.videoQuantityMode || "OUTPUT").toUpperCase() !== "OUTPUT"
);

// ============================================================================
// 分量 × 计费组合：组合轴上已单独定价时，表达同一件事的分量不再出现
// ============================================================================

/**
 * 不展示的原因：键是分量字段名，值是给用户的解释（也是清空提示的正文）。
 *
 * <p>⭐ 为什么是「不展示」而不是「标红让用户自己清」：这两组分量与组合轴是同一件事的两种表达，
 * 并存即双收；只标红的话用户会看到一个自己并不知道该怎么处理的红框，
 * 而保存时后端还会拒绝——界面上没有入口，等于卡死。不展示 + 自动清空是唯一自洽的出口。</p>
 */
const HIDDEN_REASONS: Record<string, string> = {
  videoAudioSurcharge: "有声已按单价计费，附加费会重复收取",
  videoInputVideoPerSecond: "计费量口径已折入输入秒，同一批秒不收两遍"
};

const showAudioSurcharge = computed(() => !audioPriced.value);
const showInputVideo = computed(() => !inputVideoFolded.value);

/** 当前被隐藏的分量（含标签，用于清空提示） */
const hiddenCharges = computed(() => [
  ...(showInputVideo.value ? [] : [{field: "videoInputVideoPerSecond", label: "输入视频 每秒"}]),
  ...(showAudioSurcharge.value ? [] : [{field: "videoAudioSurcharge", label: "音频直出附加费"}])
]);

/**
 * 隐藏即清空：值留在表单里会随保存落库，成为一条界面上找不到、计费却会命中的收费项。
 *
 * <p>immediate 覆盖「历史配置回显」这条路径——存量库里可能同时存在有声档位价与附加费，
 * 打开抽屉时在这里一次清掉并提示，而不是等用户点到保存才被拒。</p>
 */
watch(
  hiddenCharges,
  () => {
    const state = props.form as Record<string, unknown>;
    const cleared = hiddenCharges.value.filter((item) => state[item.field] != null);
    if (!cleared.length) {
      return;
    }
    for (const item of cleared) {
      state[item.field] = null;
    }
    ElMessage.warning(
      `已清除「${cleared.map((item) => item.label).join("、")}」：${HIDDEN_REASONS[cleared[0].field]}`
    );
  },
  {immediate: true}
);

const QUANTITY_MODES = [
  {value: "OUTPUT", label: "仅输出时长（默认）", hint: "计费量 = 输出秒数。Vidu、豆包走这条。"},
  {value: "SUM", label: "输出 + 输入", hint: "计费量 = 输出秒 + 输入秒。MiniMax 输入视频按秒走这条。"},
  {
    value: "OUTPUT_PLUS_MAX",
    label: "输出 + max(输入,输出)",
    hint: "无输入=输出；输入≤输出=输出×2；输入>输出=输入+输出。一条公式覆盖三档。"
  }
] as const;

const quantityHint = computed(
  () => QUANTITY_MODES.find((item) => item.value === props.form.videoQuantityMode)?.hint ?? ""
);
</script>

<style scoped>
.video-usage-config {
  width: 100%;
  margin-top: 4px;
}

/* 挂在档位表之后（模式组件或维度分档方案组件，取决于档位表归谁渲染），
   与外层区块标题（13px/600/primary）拉开一级层次 */
.video-usage-config__title {
  margin-bottom: 4px;
  color: var(--el-text-color-regular);
  font-size: var(--el-font-size-base, 13px);
  font-weight: 600;
}

.video-usage-config :deep(.el-divider--horizontal) {
  margin: 14px 0 12px;
}

.video-usage-config__group {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.video-usage-config__hint {
  margin-bottom: 0;
}

.video-usage-config__readonly {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 20px;
}
</style>
