<template>
  <div class="video-config">
    <el-row :gutter="16">
      <el-col :span="9">
        <el-form-item :label="`默认${unitLabel}`" label-width="160px">
          <el-input-number v-model="form.unitPrice"
                           :min="0"
                           :step="UNIT_PRICE_STEP"
                           controls-position="right"
                           style="width: 100%"/>
        </el-form-item>
      </el-col>
      <el-col :span="15">
        <el-form-item v-if="tokenPriced" label="量纲">
          <span class="video-config__readonly">
            当前按 Token 计价（{{ unitLabel }}）——分辨率与「是否含视频输入」只作分档键，不参与计量
          </span>
        </el-form-item>
      </el-col>
    </el-row>

    <!-- 维度分档：档位表是「档位 × 计费组合」整表，由方案组件托管，此处让位 -->
    <template v-if="!isDimensionTiered">
    <el-divider v-if="!tokenPriced" content-position="left">
      <span class="video-config__group">分辨率档位（{{ unitLabel }}）</span>
    </el-divider>
    <el-alert type="info" :closable="false" class="video-config__hint">
      <template #title>
        <span>
          档位名取自请求体的 <code>resolution</code> / <code>size</code> 字段，与后端归一化后的键严格一致。
        </span>
      </template>
    </el-alert>

    <!-- MiniMax 定制：档位 × 输出/输入两列，不参与「计费组合」预设 -->
    <template v-if="isVendorMiniMax">
    <div class="resolution-list">
      <el-row v-for="(row, index) in rows" :key="row.uid || `res-${index}`" :gutter="12" class="res-row">
        <el-col :xs="24" :sm="7">
        <el-form-item label="档位名" class="res-row__name" label-width="70px">
              <el-select
                :model-value="row.key"
                filterable
                allow-create
                default-first-option
                placeholder="如 768P / 2K"
                @update:model-value="(value: string) => setKey(index, value)"
              >
                <el-option v-for="item in RESOLUTION_OPTIONS" :key="item" :label="item" :value="item"/>
              </el-select>
        </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="7">
        <el-form-item label="输出每秒" class="res-row__price" label-width="76px">
              <el-input-number
                :model-value="row.price"
                :min="0"
                :step="UNIT_PRICE_STEP"
                controls-position="right"
                @update:model-value="(value: number | undefined | null) => setPrice(index, value)"
              />
        </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="8">
        <el-form-item label="输入每秒" class="res-row__price" label-width="76px">
              <el-input-number
                :model-value="row.inputPrice"
                :min="0"
                :step="UNIT_PRICE_STEP"
                controls-position="right"
                placeholder="留空同输出"
                @update:model-value="(value: number | undefined | null) => setInputPrice(index, value)"
              />
        </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="2" class="res-row__action-col">
        <el-form-item label=" " class="res-row__action" label-width="0">
          <el-button
            link
            type="danger"
            :icon="Delete"
            aria-label="删除分辨率档位"
            title="删除分辨率档位"
            @click="remove(index)"
          />
        </el-form-item>
        </el-col>
      </el-row>
    </div>

    <div class="video-config__actions">
      <el-button @click="append">新增档位</el-button>
      <el-button @click="fillCommon">填入常用档位</el-button>
      <span class="video-config__tip">档位名留空的行不会保存</span>
    </div>
    </template>

    <!-- 通用视频：先定计费组合，档位只显示已选组合的价格列 -->
    <template v-else>
      <ResolutionComboPicker :model-value="combos" :disabled="comboLocked" @update:model-value="onCombosChange"/>
      <ResolutionTierTable :rows="rows" :options="RESOLUTION_OPTIONS" :combos="combos" uid-prefix="video"/>
    </template>
    </template>

    <!-- 计费量口径是「量」的轴，按两轴分工留在本组件（维度分档只接管「价」那一半的档位表） -->
    <el-divider v-if="!tokenPriced && !isVendorMiniMax" content-position="left">
      <span class="video-config__group">计费量口径</span>
    </el-divider>
    <el-row v-if="!tokenPriced && !isVendorMiniMax" :gutter="16">
      <el-col :span="8">
        <el-form-item label="合成量模式">
          <el-select v-model="form.videoQuantityMode" style="width: 100%">
            <el-option v-for="item in QUANTITY_MODES" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="16">
        <el-form-item label="口径说明">
          <span class="video-config__readonly">{{ quantityHint }}</span>
        </el-form-item>
      </el-col>
    </el-row>

    <el-divider v-if="!tokenPriced && !isVendorMiniMax" content-position="left">
      <span class="video-config__group">输入侧分量（可选，留空不计费）</span>
    </el-divider>
    <div v-if="!tokenPriced && !isVendorMiniMax" class="input-charge-grid">
      <el-form-item v-if="!inputVideoFolded" label="输入视频 每秒">
          <el-input-number v-model="form.videoInputVideoPerSecond"
                           :min="0"
                           :step="UNIT_PRICE_STEP"
                           controls-position="right"
                           style="width: 100%"/>
      </el-form-item>
      <el-form-item label="输入图片 每张">
          <el-input-number v-model="form.videoInputImagePerImage"
                           :min="0"
                           :step="UNIT_PRICE_STEP"
                           controls-position="right"
                           style="width: 100%"/>
      </el-form-item>
      <el-form-item label="图片免费张数">
          <el-input-number v-model="form.videoFreeInputImages"
                           :min="0"
                           :step="1"
                           controls-position="right"
                           style="width: 100%"/>
      </el-form-item>
      <el-form-item label="音频直出附加费">
          <el-input-number v-model="form.videoAudioSurcharge"
                           :min="0"
                           :step="UNIT_PRICE_STEP"
                           controls-position="right"
                           style="width: 100%"/>
      </el-form-item>
    </div>
    <el-alert v-if="!tokenPriced && !isVendorMiniMax" type="info" :closable="false" class="video-config__hint">
      <template #title>
        <span>
          输入侧分量按「输出秒 + 输入秒 + 输入图片」三路各自计价后求和；
          分量单价独立于上方分辨率档，不会被默认单价顶替。附加费是与用量无关的常数项（如 Vidu 直出音频 +15）。
        </span>
      </template>
    </el-alert>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {Delete} from "@element-plus/icons-vue";
import {UNIT_PRICE_STEP, isTokenPricedUnit, unitLabelOf} from "@/utils/billingPrice";
import {COMMON_RESOLUTION_OPTIONS, RESOLUTION_COMBOS, createResolutionRow, normalizeCombos} from "@/utils/billingResolution";
import type {ResolutionComboCode, ResolutionRow} from "@/utils/billingResolution";
import ResolutionComboPicker from "../ResolutionComboPicker.vue";
import ResolutionTierTable from "../ResolutionTierTable.vue";

const props = defineProps({
  form: {type: Object, required: true}
});

/**
 * 量纲由规则单位声明：选 1M_TOKENS 就是「视频模型按 token 计价」（豆包 Seedance 口径），
 * 选 PER_SECOND 就是按秒。文案必须跟着变，否则用户会按秒的直觉去填百万 token 单价。
 */
const unitLabel = computed(() => unitLabelOf(props.form.unit));

/**
 * token 量纲下输入侧分量不参与计费：一条规则只有一个单位基数（分母），
 * 秒 / 张分量会被同一个分母除错（2026-10-26 对齐豆包口径时确立）。
 */
const tokenPriced = computed(() => isTokenPricedUnit(props.form.unit));

/**
 * MiniMax 方案（VENDOR_MINIMAX）走独立的「档位输出价 + 档位输入价」两列结构：
 * 输入视频每秒价默认取同档位输出价（可单独覆盖），图片单价/免费张数由 MiniMax 方案组件托管，
 * 音频输入免费。故本模式组件在 MiniMax 下不再渲染「合成量口径」与「输入侧分量」两块，
 * 避免与方案组件重复编辑同一字段。
 */
const isVendorMiniMax = computed(() => String(props.form.pricingPlan || "").toUpperCase() === "VENDOR_MINIMAX");

/**
 * 维度分档价（DIMENSION_TIERED）：档位表由方案组件（PricingPlanDimensionTieredConfig）托管，
 * 本组件只让位<b>档位表</b>；计费量口径与输入侧分量仍然保留——
 * 本方案只规定「单价取哪一档」，不否认输入秒独立成价（口径为「仅输出」时二者并存是合法的）。
 */
const isDimensionTiered = computed(() => String(props.form.pricingPlan || "").toUpperCase() === "DIMENSION_TIERED");

/**
 * 输入秒是否已被计费量口径折进输出量。
 *
 * <p>折进去之后再按 {@code 输入视频 每秒} 单独收一次，同一批秒会收两遍；
 * 后端 {@code BillingConfig#validateDimensionTiered} 同口径拒存，这里直接不给填。</p>
 */
const inputVideoFolded = computed(
  () => isDimensionTiered.value && String(props.form.videoQuantityMode || "OUTPUT").toUpperCase() !== "OUTPUT"
);

/** 常用档位建议（与维度分档方案组件共用同一份，见 utils/billingResolution） */
const RESOLUTION_OPTIONS = COMMON_RESOLUTION_OPTIONS;

/**
 * 合成量口径：一条公式覆盖「输入≤输出时输出×2、输入>输出时输入+输出」三档条件，与后端 VideoConfig.quantity 同口径。
 */
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

const rows = computed<ResolutionRow[]>(() =>
  Array.isArray(props.form.videoResolutionRows) ? props.form.videoResolutionRows : []
);

/** 当前预设的计费组合（决定档位表列集）；默认全选 */
const combos = computed<ResolutionComboCode[]>(() => normalizeCombos(props.form.videoDimensionCombos));

/** 是否已有档位：任一档位行填了名字即锁定组合（改组合会让已填价格错位到别的键） */
const comboLocked = computed(() => rows.value.some((row) => String(row.key || "").trim() !== ""));

/**
 * 组合变更：同步到 form，并清掉「被取消组合」在所有行上的价格。
 *
 * <p>不清的话，取消勾选只是让列消失，价格仍留在行里 → 保存时写进 resolutionPrices，
 * 成为界面上看不见、计费却会命中的价（静默收费）。</p>
 */
function onCombosChange(codes: ResolutionComboCode[]) {
  const normalized = normalizeCombos(codes);
  props.form.videoDimensionCombos = normalized;
  for (const combo of RESOLUTION_COMBOS) {
    if (!normalized.includes(combo.code)) {
      for (const row of rows.value) {
        row[combo.field] = null;
      }
    }
  }
}

let sequence = 0;

function nextUid(): string {
  sequence += 1;
  return `res-${Date.now()}-${sequence}`;
}

function append() {
  rows.value.push(createResolutionRow("", nextUid()));
}

function remove(index: number) {
  rows.value.splice(index, 1);
}

function setKey(index: number, value: string) {
  const row = rows.value[index];
  if (row) {
    row.key = String(value || "").trim().toUpperCase();
  }
}

/** MiniMax 输出每秒价（通用视频的单价列由 ResolutionTierTable 内部维护） */
function setPrice(index: number, value: number | undefined | null) {
  const row = rows.value[index];
  if (row) {
    row.price = value == null ? null : value;
  }
}

/** MiniMax 输入视频每秒价：留空表示沿用同档位输出价 */
function setInputPrice(index: number, value: number | undefined | null) {
  const row = rows.value[index];
  if (row) {
    row.inputPrice = value == null ? null : value;
  }
}

/** 一键铺开常用档位（只补不存在的，不覆盖已填价格） */
function fillCommon() {
  const existing = new Set(rows.value.map((row) => row.key.toUpperCase()));
  for (const key of RESOLUTION_OPTIONS) {
    if (!existing.has(key)) {
      rows.value.push(createResolutionRow(key, nextUid()));
    }
  }
}
</script>

<style scoped>
.video-config {
  width: 100%;
}

.video-config__group {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.video-config__hint {
  margin-bottom: 12px;
}

.video-config__readonly {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 20px;
}

.res-row {
  padding: 10px 12px 0;
  margin-bottom: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}

.res-row :deep(.el-form-item) {
  margin-bottom: 12px;
}


.res-row__name {
  min-width: 0;
}

.res-row__price {
  min-width: 0;
}

.res-row__action {
  min-width: 0;
}

.res-row__action-col {
  display: flex;
  align-items: flex-start;
}

.video-config__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.video-config__tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.input-charge-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0 16px;
}

@media (max-width: 760px) {
  .input-charge-grid {
    grid-template-columns: 1fr;
  }
}
</style>
