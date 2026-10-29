<template>
  <div class="dimension-tiered-config">
    <el-alert type="info" :closable="false" class="dimension-tiered-config__formula">
      <template #title>
        <div class="dimension-tiered-config__formula-title">维度分档价</div>
        <div class="dimension-tiered-config__formula-body">总价 = 计费量 × 档位单价 + 输入侧分量 + 附加费</div>
        <div class="dimension-tiered-config__formula-note">
          档位单价按「分辨率 × 是否输出声音 × 有无输入/参考视频」取值，逐级去尾回落：
          含参考 + 有声 → 含参考 → 有声 → 无声 → 默认单价。
          计费量口径与输入侧分量在上方「基础计费规则」里配，本方案只决定<b>单价取哪一档</b>。
        </div>
      </template>
    </el-alert>

    <el-divider content-position="left">
      <span class="dimension-tiered-config__group">支持的分辨率档位</span>
    </el-divider>

    <ResolutionTierPicker :rows="rows" tip="取消勾选即删除该档位（含已填单价）"/>

    <el-divider content-position="left">
      <span class="dimension-tiered-config__group">档位单价（{{ unitLabel }}）</span>
    </el-divider>

    <ResolutionTierTable :rows="rows" :columns="RESOLUTION_PRICE_COLUMNS.DIMENSION"/>

    <el-alert type="warning" :closable="false" class="dimension-tiered-config__hint">
      <template #title>
        <span>
          默认单价必填——厂商档位不可枚举，配不到的档会回落到它；留空等于「识别不了的请求免费出账」。
          「有声」两列只在模型真的能直出音频时才填（阿里云万相按 <code>audio</code> 分档），
          留空即与无声同价。
        </span>
      </template>
    </el-alert>

    <el-divider content-position="left">
      <span class="dimension-tiered-config__group">参考样例（阿里云百炼 · 万相图生视频）</span>
    </el-divider>
    <el-table :data="REFERENCE_ITEMS" size="small" border class="dimension-tiered-config__reference">
      <el-table-column prop="model" label="模型" min-width="150"/>
      <el-table-column prop="silent" label="无声" min-width="130"/>
      <el-table-column prop="sound" label="有声" min-width="130"/>
      <el-table-column prop="note" label="备注" min-width="180"/>
    </el-table>
    <div class="dimension-tiered-config__sample-note">
      样例口径为「仅输出时长计费」（首帧输入图不计费），计费量口径选 <code>OUTPUT</code>、
      输入侧分量留空即可完整表达。
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {unitLabelOf} from "@/utils/billingPrice";
import {RESOLUTION_PRICE_COLUMNS} from "@/utils/billingResolution";
import type {ResolutionRow} from "@/utils/billingResolution";
import ResolutionTierPicker from "../ResolutionTierPicker.vue";
import ResolutionTierTable from "../ResolutionTierTable.vue";

/**
 * 维度分档价组件（方案轴：pricingPlan = DIMENSION_TIERED）。
 *
 * <p>⭐ 为什么做成独立方案而不是继续塞在固定价里：视频计费的「分档」有<b>三根正交的轴</b>——
 * {@code TIERED} 按<b>用量</b>分档、{@code PEAK_OFF_PEAK} 按<b>时间</b>分档、本方案按
 * <b>请求属性</b>（分辨率 × 有声 × 有无输入视频）分档。前两根各自解决一类问题，
 * 谁也表达不了「同一秒视频、1080P 有声比 720P 无声贵三倍」这种价表。</p>
 *
 * <p><b>本方案的真正增量是保存期的强校验，不是新的取价算法</b>——取价与固定价完全一致
 * （都走 {@code FixedPricing}），但维度键是自由文本、取价是「逐级去尾找第一个命中」，
 * 键拼错时固定价会<b>静默回落默认价</b>（配置看着生效、那档价从未命中），
 * 本方案在 {@code BillingConfig#validateDimensionTiered} 直接拒绝保存。</p>
 *
 * <p>档位价分两步配：先在 {@link ResolutionTierPicker} 选「支持哪些档位」，
 * 再由 {@link ResolutionTierTable} 按「档位 × 有声 × 含参考」四列填价——
 * 与通用视频方案共用同一张表、同一套键契约，只是列集更宽（通用方案只要「单价 / 含参考输入」两列）。
 * 计费量口径与输入侧分量留在视频模式组件——它们是「量」的轴，按两轴分工不属于本方案。</p>
 */
const props = defineProps({
  form: {type: Object, required: true}
});

/** 量纲文案跟着规则单位走：选 1M_TOKENS 就是「每百万 Token 单价」 */
const unitLabel = computed(() => unitLabelOf(props.form.unit));

/**
 * 行字段与 {@code resolutionPrices} 的键一一对应（契约见 utils/billingResolution）：
 * price → {@code 档位}、audioPrice → {@code 档位:AUDIO}、
 * referencePrice → {@code 档位:REFERENCE}、referenceAudioPrice → {@code 档位:REFERENCE:AUDIO}。
 * 行的增删在 {@link ResolutionTierPicker}，价格录入在 {@link ResolutionTierTable}（两者共用本契约）。
 */
const rows = computed<ResolutionRow[]>(() =>
  Array.isArray(props.form.videoResolutionRows) ? props.form.videoResolutionRows : []
);

/**
 * 阿里云百炼「万相-图生视频-基于首帧」官方价（2026-10），用来对齐本方案的键空间形状。
 *
 * <p>价表是「分辨率 × 有无声音」两维，输入是首帧图、完全不计费——
 * 这佐证了「仅输出计费」才是默认，MiniMax 那种「输入图片按张计费」是特例。</p>
 */
const REFERENCE_ITEMS = [
  {model: "wan2.6-i2v-flash", silent: "720P 0.15 / 1080P 0.25", sound: "720P 0.30 / 1080P 0.50", note: "每秒，免费额度 50 秒"},
  {model: "wan2.6-i2v", silent: "720P 0.30 / 1080P 0.50", sound: "720P 0.60 / 1080P 1.00", note: "每秒"}
];
</script>

<style scoped>
.dimension-tiered-config {
  width: 100%;
  margin-top: 4px;
}

.dimension-tiered-config__formula {
  margin-bottom: 12px;
}

.dimension-tiered-config__formula-title {
  font-size: 13px;
  font-weight: 600;
}

.dimension-tiered-config__formula-body {
  margin-top: 4px;
  font-family: var(--el-font-family-monospace, monospace);
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.dimension-tiered-config__formula-note {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 20px;
}

.dimension-tiered-config__group {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.dimension-tiered-config__hint {
  margin-top: 10px;
}

.dimension-tiered-config__sample-note {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 20px;
}
</style>
