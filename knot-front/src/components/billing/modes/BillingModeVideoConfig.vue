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
    <!-- 固定价：档位表同样不呈现——固定价语义是「一个单价直出」，见 isFixedPlan 注释 -->
    <template v-if="!isDimensionTiered && !isFixedPlan">
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
                <el-option v-for="item in tierOptions" :key="item" :label="item" :value="item"/>
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
      <ResolutionTierTable :rows="rows" :combos="combos" uid-prefix="video"/>
    </template>
    </template>

    <!-- 计费量与输入侧分量：视频模式「量」的另一半，与档位价成对出现。
         ⭐ 归属跟<b>档位表</b>走：档位表在本组件（FIXED/TIERED/PEAK_OFF_PEAK）时挂在这里；
         DIMENSION_TIERED 档位表让位给方案组件，用量区块随之搬过去——
         「价」在卡片外、「量」留在基础卡片内，等于把同一条价表拆成两个区域看。
         不能外挂到 BillingRuleFormDialog——否则容器层要知道视频模式的内部构成 -->
    <BillingModeVideoUsageConfig v-if="!isDimensionTiered" :form="form"/>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {ElMessage} from "element-plus";
import {Delete} from "@element-plus/icons-vue";
import {UNIT_PRICE_STEP, isTokenPricedUnit, unitLabelOf} from "@/utils/billingPrice";
import {applyCombosChange, createResolutionRow, normalizeCombos} from "@/utils/billingResolution";
import {useResolutionTiers} from "@/composables/useResolutionTiers";
import type {CombosFormSlice, ResolutionComboCode, ResolutionRow} from "@/utils/billingResolution";
import ResolutionComboPicker from "../ResolutionComboPicker.vue";
import ResolutionTierTable from "../ResolutionTierTable.vue";
import BillingModeVideoUsageConfig from "./BillingModeVideoUsageConfig.vue";

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
 * 音频输入免费。计量字段统一由下方 BillingModeVideoUsageConfig 托管，本组件不重复渲染。
 */
const isVendorMiniMax = computed(() => String(props.form.pricingPlan || "").toUpperCase() === "VENDOR_MINIMAX");

/**
 * 维度分档价（DIMENSION_TIERED）：档位表与用量区块一并让位给方案组件（PricingPlanDimensionTieredConfig）——
 * 后者是「量」的轴，必须紧贴它所配的档位表，不能留在基础卡片里。本组件在此方案下只留默认单价。
 */
const isDimensionTiered = computed(() => String(props.form.pricingPlan || "").toUpperCase() === "DIMENSION_TIERED");

/**
 * 固定价（FIXED）不呈现档位（2026-10-30 定调）：档位表属于「按请求属性分档」，只归维度分档价。
 *
 * <p>理由：固定价语义是「一个单价直出」；档位表一旦存在就必然参与取价（{@code FixedPricing} 同一份
 * 实现按 {@code resolutionPrices} 命中），而固定价下界面上没有它的位置——
 * 看得见的价格和库里会命中的价格必须是同一张表，否则就是静默收费。</p>
 *
 * <p>空方案按固定价处理（与后端 {@code PricingPlanEnum.fromCode} 同口径）：漏配时宁可少显示档位，
 * 也不能让一张看不见的表悄悄生效。</p>
 */
const isFixedPlan = computed(() => {
  const plan = String(props.form.pricingPlan || "").toUpperCase();
  return plan === "" || plan === "FIXED";
});

/** 档位建议来自后端 ResolutionTierEnum（不是白名单，下拉仍允许自由输入） */
const {tierOptions} = useResolutionTiers();

const rows = computed<ResolutionRow[]>(() =>
  Array.isArray(props.form.videoResolutionRows) ? props.form.videoResolutionRows : []
);

/** 当前预设的计费组合（决定档位表列集）；新建默认只有基础价，见 utils/billingResolution#DEFAULT_COMBOS */
const combos = computed<ResolutionComboCode[]>(() => normalizeCombos(props.form.videoDimensionCombos));

/** 是否已有档位：任一档位行填了名字即锁定组合（改组合会让已填价格错位到别的键） */
const comboLocked = computed(() => rows.value.some((row) => String(row.key || "").trim() !== ""));

/**
 * 组合变更：统一走 {@link applyCombosChange}（与维度分档方案组件同一份实现）。
 *
 * <p>它负责三件会直接影响金额的事：归一后写回、清掉被取消组合在所有行上的价格
 * （否则保存后是界面上看不见、计费却会命中的价）、无「含参考」时把量口径回落。
 * 勾上有声单列时顺带清空音频直出附加费——两者是同一件事，并存即双收，故这里必须提示。</p>
 */
function onCombosChange(codes: ResolutionComboCode[]) {
  const {clearedAudioSurcharge} = applyCombosChange(props.form as CombosFormSlice, codes);
  if (clearedAudioSurcharge) {
    ElMessage.warning("已清除「音频直出附加费」：有声已按单价计费，同一项不重复收");
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
  for (const key of tierOptions.value) {
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

</style>
