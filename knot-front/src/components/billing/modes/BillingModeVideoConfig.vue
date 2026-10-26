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
        <el-form-item label="单价" class="res-row__price" label-width="60px">
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
        <el-form-item label="含参考输入" class="res-row__price" label-width="100px">
              <el-input-number
                :model-value="row.referencePrice"
                :min="0"
                :step="UNIT_PRICE_STEP"
                controls-position="right"
                placeholder="留空同价"
                @update:model-value="(value: number | undefined | null) => setReferencePrice(index, value)"
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

    <el-divider v-if="!tokenPriced" content-position="left">
      <span class="video-config__group">计费量口径</span>
    </el-divider>
    <el-row v-if="!tokenPriced" :gutter="16">
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

    <el-divider v-if="!tokenPriced" content-position="left">
      <span class="video-config__group">输入侧分量（可选，留空不计费）</span>
    </el-divider>
    <div v-if="!tokenPriced" class="input-charge-grid">
      <el-form-item label="输入视频 每秒">
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
    <el-alert v-if="!tokenPriced" type="info" :closable="false" class="video-config__hint">
      <template #title>
        <span>
          输入侧分量按「输出秒 + 输入秒 + 输入图片」三路各自计价后求和（MiniMax Hailuo 口径）；
          分量单价独立于上方分辨率档，不会被默认单价顶替。附加费是与用量无关的常数项（Vidu 直出音频 +15）。
        </span>
      </template>
    </el-alert>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {Delete} from "@element-plus/icons-vue";
import {UNIT_PRICE_PRECISION, UNIT_PRICE_STEP} from "@/utils/billingPrice";
import {BillingUnit} from "@/constants/status";

const props = defineProps({
  form: {type: Object, required: true}
});

/**
 * 量纲由规则单位声明：选 1M_TOKENS 就是「视频模型按 token 计价」（豆包 Seedance 口径），
 * 选 PER_SECOND 就是按秒。文案必须跟着变，否则用户会按秒的直觉去填百万 token 单价。
 */
const UNIT_LABELS: Record<string, string> = {
  [BillingUnit.PER_SECOND]: "每秒单价",
  [BillingUnit.ONE_M_TOKENS]: "每百万 Token 单价",
  [BillingUnit.ONE_K_TOKENS]: "每千 Token 单价",
  [BillingUnit.PER_TOKEN]: "每 Token 单价"
};

const unitLabel = computed(() => UNIT_LABELS[String(props.form.unit || BillingUnit.PER_SECOND)] || "每秒单价");

/**
 * token 量纲下输入侧分量不参与计费：一条规则只有一个单位基数（分母），
 * 秒 / 张分量会被同一个分母除错（2026-10-26 对齐豆包口径时确立）。
 */
const tokenPriced = computed(() => {
  const unit = String(props.form.unit || BillingUnit.PER_SECOND);
  return unit === BillingUnit.ONE_M_TOKENS || unit === BillingUnit.ONE_K_TOKENS || unit === BillingUnit.PER_TOKEN;
});

/**
 * 常用档位快捷选项：**只是下拉建议，不是合法值白名单**。
 *
 * <p>厂商档位不可枚举——MiniMax 用 768P、Vidu 用 540P、豆包用 480p/720p/1080p，
 * 还有各种自造名。硬编码成固定档位会让新档位永远配不上价（2026-10-26 修的就是这个），
 * 故档位名可自由输入（el-select allow-create）。</p>
 */
const RESOLUTION_OPTIONS = ["480P", "540P", "720P", "768P", "1080P", "2K", "4K"];

/** 单行分辨率档位：档位名 + 单价 + 含参考输入时的单价 */
interface ResolutionRow {
  uid?: string;
  key: string;
  price: number | null;
  referencePrice: number | null;
}

/**
 * 合成量口径：一条公式覆盖 Kling 三档条件，与后端 VideoConfig.quantity 同口径。
 */
const QUANTITY_MODES = [
  {value: "OUTPUT", label: "仅输出时长（默认）", hint: "计费量 = 输出秒数。Vidu、豆包走这条。"},
  {value: "SUM", label: "输出 + 输入", hint: "计费量 = 输出秒 + 输入秒。MiniMax 输入视频按秒走这条。"},
  {
    value: "OUTPUT_PLUS_MAX",
    label: "Kling 口径（输出 + max(输入,输出)）",
    hint: "无输入=输出；输入≤输出=输出×2；输入>输出=输入+输出。一条公式覆盖 Kling 三档。"
  }
] as const;

const quantityHint = computed(
  () => QUANTITY_MODES.find((item) => item.value === props.form.videoQuantityMode)?.hint ?? ""
);

const rows = computed<ResolutionRow[]>(() =>
  Array.isArray(props.form.videoResolutionRows) ? props.form.videoResolutionRows : []
);

let sequence = 0;

function nextUid(): string {
  sequence += 1;
  return `res-${Date.now()}-${sequence}`;
}

function append() {
  rows.value.push({uid: nextUid(), key: "", price: null, referencePrice: null});
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

function setPrice(index: number, value: number | undefined | null) {
  const row = rows.value[index];
  if (row) {
    row.price = value == null ? null : value;
  }
}

function setReferencePrice(index: number, value: number | undefined | null) {
  const row = rows.value[index];
  if (row) {
    row.referencePrice = value == null ? null : value;
  }
}

/** 一键铺开常用档位（只补不存在的，不覆盖已填价格） */
function fillCommon() {
  const existing = new Set(rows.value.map((row) => row.key.toUpperCase()));
  for (const key of RESOLUTION_OPTIONS) {
    if (!existing.has(key)) {
      rows.value.push({uid: nextUid(), key, price: null, referencePrice: null});
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
