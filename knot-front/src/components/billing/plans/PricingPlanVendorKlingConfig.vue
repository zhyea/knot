<template>
  <div class="vendor-kling-config">
    <el-alert type="info" :closable="false" class="vendor-kling-config__formula">
      <template #title>
        <div class="vendor-kling-config__formula-title">Kling 分档按秒计费口径</div>
        <div class="vendor-kling-config__formula-body">总价 = 计费量 × 档位单价</div>
        <div class="vendor-kling-config__formula-note">
          档位单价按「有声 / 含参考」组合取值，未填的列逐级回落（含参考+有声 → 含参考 → 有声 → 无声）；
          输入秒不单独计价，只按下方口径折进计费量。
        </div>
      </template>
    </el-alert>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="8">
        <el-form-item label="计费量口径" label-width="96px">
          <el-select v-model="form.videoQuantityMode" style="width: 100%">
            <el-option v-for="item in QUANTITY_MODES" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :xs="24" :sm="16">
        <el-form-item label="口径说明" label-width="80px">
          <span class="vendor-kling-config__readonly">{{ quantityHint }}</span>
        </el-form-item>
      </el-col>
    </el-row>

    <el-divider content-position="left">
      <span class="vendor-kling-config__group">分辨率档位（每秒）</span>
    </el-divider>

    <div class="kling-res">
      <div class="kling-res__head">
        <span>档位名</span>
        <span>无声</span>
        <span>有声</span>
        <span>含参考</span>
        <span>含参考 + 有声</span>
        <span/>
      </div>
      <div v-for="(row, index) in rows" :key="row.uid || `kling-res-${index}`" class="kling-res__row">
        <el-select
          :model-value="row.key"
          filterable
          allow-create
          default-first-option
          placeholder="如 720P / 4K"
          @update:model-value="(value: string) => setKey(index, value)"
        >
          <el-option v-for="item in RESOLUTION_OPTIONS" :key="item" :label="item" :value="item"/>
        </el-select>
        <el-input-number
          :model-value="row.price"
          :min="0"
          :step="UNIT_PRICE_STEP"
          controls-position="right"
          @update:model-value="(value: number | undefined | null) => setPrice(index, 'price', value)"
        />
        <el-input-number
          :model-value="row.audioPrice"
          :min="0"
          :step="UNIT_PRICE_STEP"
          controls-position="right"
          placeholder="留空同无声"
          @update:model-value="(value: number | undefined | null) => setPrice(index, 'audioPrice', value)"
        />
        <el-input-number
          :model-value="row.referencePrice"
          :min="0"
          :step="UNIT_PRICE_STEP"
          controls-position="right"
          placeholder="留空同无声"
          @update:model-value="(value: number | undefined | null) => setPrice(index, 'referencePrice', value)"
        />
        <el-input-number
          :model-value="row.referenceAudioPrice"
          :min="0"
          :step="UNIT_PRICE_STEP"
          controls-position="right"
          placeholder="留空逐级回落"
          @update:model-value="(value: number | undefined | null) => setPrice(index, 'referenceAudioPrice', value)"
        />
        <el-button
          link
          type="danger"
          :icon="Delete"
          aria-label="删除分辨率档位"
          title="删除分辨率档位"
          @click="remove(index)"
        />
      </div>
    </div>

    <div class="vendor-kling-config__actions">
      <el-button @click="append">新增档位</el-button>
      <el-button @click="fillCommon">填入常用档位</el-button>
      <span class="vendor-kling-config__tip">档位名留空的行不会保存</span>
    </div>

    <el-divider content-position="left">
      <span class="vendor-kling-config__group">非视频计费项（只作参考，需另建规则）</span>
    </el-divider>
    <el-table :data="NON_VIDEO_ITEMS" size="small" border class="vendor-kling-config__reference">
      <el-table-column prop="item" label="计费项" min-width="130"/>
      <el-table-column prop="price" label="官方价" min-width="150"/>
      <el-table-column prop="mode" label="建议模式" width="100"/>
      <el-table-column prop="unit" label="建议单位" min-width="130"/>
      <el-table-column prop="note" label="备注" min-width="170"/>
    </el-table>
    <el-alert type="warning" :closable="false" class="vendor-kling-config__hint">
      <template #title>
        <span>
          上表的数字人 / 对口型 / 语音合成 / 音效 / 图像识别与视频按秒口径不同（按次或按 5 秒），
          无法共用一条规则，请为每项单独建规则（模式与单位照上表选）。
          「对口型 0.5 / 5 秒」须在上表折算成每秒 0.1 后填写——系统没有「每 5 秒」这个单位基数。
        </span>
      </template>
    </el-alert>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {Delete} from "@element-plus/icons-vue";
import {UNIT_PRICE_STEP} from "@/utils/billingPrice";
import {createResolutionRow} from "@/utils/billingResolution";
import type {ResolutionPriceField, ResolutionRow} from "@/utils/billingResolution";

/**
 * Kling 专属计费组件（方案轴：pricingPlan = VENDOR_KLING）。
 *
 * <p>⭐ 为什么整张档位表由本组件承担，而不是复用视频模式组件的档位表：Kling 的单价是
 * <b>「档位 × 有声 × 含参考」三维</b>（3.0 Omni 四档共存、4.0 再按有无输入视频分档），
 * 通用表只有「档位 + 含参考」两列，表达不了有声轴；把它硬塞进通用表会出现
 * 「同一档位只有一个价」的静默丢配置。故本组件渲染 5 列整表，
 * 视频模式组件在该方案下隐藏自己的档位表与输入侧分量（见 BillingModeVideoConfig 的 isVendorKling 分支）。</p>
 *
 * <p>行数据仍然共用 {@code form.videoResolutionRows}——它是 {@code config_json.resolutionPrices}
 * 的唯一表单映射，序列化与回显都在 BillingRuleFormDialog 的 parseResolutionRows / buildBaseConfig，
 * 本组件只负责编辑行字段，不各自维护一套档位数据（否则保存时两套数据会互相覆盖）。</p>
 */
const props = defineProps({
  form: {type: Object, required: true}
});

/**
 * 计费量口径：Kling 官方只有两种，且无法从价表反推——
 * 在线模型按输出时长，Kling 4.0 把输入时长折进去（输入≤输出=输出×2；输入>输出=输入+输出）。
 * 故意不提供 SUM：它在「输入≤输出」档会少收一半。
 */
const QUANTITY_MODES = [
  {value: "OUTPUT", label: "仅输出时长（在线模型）", hint: "计费量 = 输出秒数。3.0 / Turbo / Omni / O1 / 2.6 / 2.5 走这条。"},
  {
    value: "OUTPUT_PLUS_MAX",
    label: "Kling 4.0（输出 + max(输入,输出)）",
    hint: "无输入=输出；输入≤输出=输出×2；输入>输出=输入+输出。单价走「含参考」列的无输入/有输入分档价。"
  }
] as const;

const quantityHint = computed(
  () => QUANTITY_MODES.find((item) => item.value === props.form.videoQuantityMode)?.hint ?? ""
);

/** 常用档位只是下拉建议，不是合法值白名单（厂商档位不可枚举，档位名可自由输入） */
const RESOLUTION_OPTIONS = ["720P", "1080P", "4K"];

/**
 * 行字段与 {@code resolutionPrices} 的键一一对应（契约见 utils/billingResolution）：
 * price → {@code 档位}、audioPrice → {@code 档位:AUDIO}、
 * referencePrice → {@code 档位:REFERENCE}、referenceAudioPrice → {@code 档位:REFERENCE:AUDIO}。
 */
const rows = computed<ResolutionRow[]>(() =>
  Array.isArray(props.form.videoResolutionRows) ? props.form.videoResolutionRows : []
);

let sequence = 0;

function nextUid(): string {
  sequence += 1;
  return `kling-res-${Date.now()}-${sequence}`;
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

function setPrice(index: number, field: ResolutionPriceField, value: number | undefined | null) {
  const row = rows.value[index];
  if (row) {
    row[field] = value == null ? null : value;
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

/**
 * 非视频计费项官方价（klingai.com/document-api/pricing/base/video.md，2026-10）。
 *
 * <p>这些项与视频按秒口径不同（按次 / 按 5 秒），共用一条规则算不出来，故只在本组件给出
 * 官方价与建议配置，实际计价靠各模式自己的规则。对齐原则：金额进公式前必须先归一成
 * 「每单位基数」的价——「每 5 秒 0.5」等价于每秒 0.1，绝不能让 5 进金额公式。</p>
 */
const NON_VIDEO_ITEMS = [
  {item: "数字人", price: "0.4（720P）/ 0.8（1080P）每秒", mode: "VIDEO", unit: "PER_SECOND", note: "按输出视频秒数计"},
  {item: "语音合成", price: "0.05 / 次", mode: "REQUEST", unit: "PER_REQUEST", note: "数字人附带项，按次"},
  {item: "对口型", price: "0.5 / 5 秒", mode: "VIDEO", unit: "PER_SECOND", note: "折算每秒 0.1 后填单价"},
  {item: "人脸识别", price: "0.05 / 次", mode: "REQUEST", unit: "PER_REQUEST", note: "对口型附带项，按次"},
  {item: "音频生成 · 文生音效", price: "0.25 / 次", mode: "REQUEST", unit: "PER_REQUEST", note: "按次"},
  {item: "音频生成 · 视频生音效", price: "0.25 / 次", mode: "REQUEST", unit: "PER_REQUEST", note: "按次"},
  {item: "音频生成 · 音色定制", price: "0.05 / 次", mode: "REQUEST", unit: "PER_REQUEST", note: "按次"},
  {item: "图像识别", price: "0.1 / 次", mode: "REQUEST", unit: "PER_REQUEST", note: "按次"}
];
</script>

<style scoped>
.vendor-kling-config {
  width: 100%;
  margin-top: 4px;
}

.vendor-kling-config__formula {
  margin-bottom: 12px;
}

.vendor-kling-config__formula-title {
  font-size: 13px;
  font-weight: 600;
}

.vendor-kling-config__formula-body {
  margin-top: 4px;
  font-family: var(--el-font-family-monospace, monospace);
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.vendor-kling-config__formula-note {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.vendor-kling-config__group {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.vendor-kling-config__readonly {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 20px;
}

.vendor-kling-config__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.vendor-kling-config__tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.vendor-kling-config__hint {
  margin-top: 10px;
}

.kling-res {
  width: 100%;
}

.kling-res__head,
.kling-res__row {
  display: grid;
  grid-template-columns: 1.3fr 1fr 1fr 1fr 1.3fr 34px;
  gap: 8px;
  align-items: center;
}

.kling-res__head {
  padding: 0 12px 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.kling-res__row {
  padding: 10px 12px;
  margin-bottom: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}

.kling-res__row :deep(.el-input-number) {
  width: 100%;
}

@media (max-width: 900px) {
  .kling-res__head {
    display: none;
  }

  .kling-res__row {
    grid-template-columns: 1fr;
  }
}
</style>
