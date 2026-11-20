<template>
  <div class="image-tiered-config">
    <el-alert type="info" :closable="false" class="image-tiered-config__formula">
      <template #title>
        <div class="image-tiered-config__formula-title">维度分档价（图片）</div>
        <div class="image-tiered-config__formula-body">总价 = 出图张数 × 像素档单价 + 输入图片张数 × 每张单价</div>
        <div class="image-tiered-config__formula-note">
          像素档按输出图片的像素总量（宽 × 高）落档，命中第一个「上界不小于实际像素」的档；上界留空表示上不封顶。
          有档位表却没有档覆盖、且上游回了像素时，计价直接报错，不静默回落默认单价。
        </div>
      </template>
    </el-alert>

    <el-divider content-position="left">
      <span class="image-tiered-config__group">输出像素档位（每张单价）</span>
    </el-divider>
    <div class="image-tiered-config__head">
      <span>像素上界</span>
      <span>每张单价</span>
      <span/>
    </div>
    <div v-for="(row, index) in rows" :key="row.uid || `img-tier-${index}`" class="image-tiered-config__row">
      <el-input-number
        :model-value="row.maxPixels"
        :min="1"
        :step="100000"
        placeholder="留空 = 上不封顶"
        @update:model-value="(value: number | undefined | null) => setMaxPixels(index, value)"
      />
      <el-input-number
        :model-value="row.price"
        :min="0"
        :step="UNIT_PRICE_STEP"
        controls-position="right"
        placeholder="必填"
        @update:model-value="(value: number | undefined | null) => setPrice(index, value)"
      />
      <el-button
        link
        type="danger"
        :icon="Delete"
        aria-label="删除像素档位"
        title="删除像素档位"
        @click="remove(index)"
      />
    </div>
    <div class="image-tiered-config__actions">
      <el-button @click="append">新增档位</el-button>
      <span class="image-tiered-config__tip">像素上界留空的行必须放在最后一行</span>
    </div>

    <el-divider content-position="left">
      <span class="image-tiered-config__group">输入图片分量（可选，留空不计费）</span>
    </el-divider>
    <el-row :gutter="16">
      <el-col :xs="24" :span="8">
        <el-form-item label="输入图片 每张">
          <el-input-number
            v-model="form.imageInputImagePerImage"
            :min="0"
            :step="UNIT_PRICE_STEP"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :span="8">
        <el-form-item label="图片免费张数">
          <el-input-number
            v-model="form.imageFreeInputImages"
            :min="0"
            :step="1"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
    </el-row>
    <el-alert type="info" :closable="false" class="image-tiered-config__hint">
      <template #title>
        <span>
          输入图片张数取自请求体的 {{ INPUT_IMAGE_KEYS }} 等字段，单图字段按 1 张计；
          「首张免费、第二张起收费」填免费张数 1。两者都留空时输入图片不计费。
        </span>
      </template>
    </el-alert>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {Delete} from "@element-plus/icons-vue";
import {UNIT_PRICE_STEP} from "@/utils/billingPrice";
import {createUidGenerator} from "@/utils/billingResolution";
import type {ImageTierRow} from "@/utils/billingImage";

/**
 * 图片维度分档价（pricingPlan = DIMENSION_TIERED 且 billingMode = IMAGE）。
 *
 * <p>与视频方案组件的分工一致：本组件只决定「单价取哪一档」与输入侧分量，
 * 出图张数由 IMAGE 模式本身决定（一次请求出了几张图）。</p>
 *
 * <p>⚠ 像素档位表只允许出现在维度分档价方案下：后端 {@code validateImage} 与
 * {@code image.tiers is only allowed for DIMENSION_TIERED plan} 同口径拒存，
 * 避免固定价下配出「界面上看不见、取价却会命中」的档位表。</p>
 */
const props = defineProps({
  form: {type: Object, required: true}
});

/** 计入输入图片张数的请求体字段，与后端 {@code MediaUsageReaders.imageCount} 同源 */
const INPUT_IMAGE_KEYS = "images / input_images / reference_images / image_urls";

/** 表单切片：`form` 由宿主以 Object 传入，取字段走这里，避免散落 any 断言 */
function state(): Record<string, unknown> {
  return props.form as Record<string, unknown>;
}

function tierRows(): ImageTierRow[] {
  const source = state().imageTiers;
  return Array.isArray(source) ? (source as ImageTierRow[]) : [];
}

const rows = computed<ImageTierRow[]>(() => tierRows());

const nextUid = createUidGenerator("img-tier");

function append() {
  const form = state();
  if (!Array.isArray(form.imageTiers)) {
    form.imageTiers = [];
  }
  (form.imageTiers as ImageTierRow[]).push({uid: nextUid(), maxPixels: null, price: null});
}

function remove(index: number) {
  tierRows().splice(index, 1);
}

function setMaxPixels(index: number, value: number | undefined | null) {
  const row = rows.value[index];
  if (row) {
    row.maxPixels = value == null ? null : value;
  }
}

function setPrice(index: number, value: number | undefined | null) {
  const row = rows.value[index];
  if (row) {
    row.price = value == null ? null : value;
  }
}
</script>

<style scoped>
.image-tiered-config {
  width: 100%;
  margin-top: 4px;
}

.image-tiered-config__formula {
  margin-bottom: 12px;
}

.image-tiered-config__formula-title {
  font-size: 13px;
  font-weight: 600;
}

.image-tiered-config__formula-body {
  margin-top: 4px;
  font-family: var(--el-font-family-monospace, monospace);
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.image-tiered-config__formula-note {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 20px;
}

.image-tiered-config__group {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.image-tiered-config__head,
.image-tiered-config__row {
  display: grid;
  grid-template-columns: 1fr 1fr 34px;
  gap: 8px;
  align-items: center;
}

.image-tiered-config__head {
  padding: 0 12px 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.image-tiered-config__row {
  padding: 10px 12px;
  margin-bottom: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}

.image-tiered-config__row :deep(.el-input-number) {
  width: 100%;
}

.image-tiered-config__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.image-tiered-config__tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.image-tiered-config__hint {
  margin-top: 10px;
}

@media (max-width: 900px) {
  .image-tiered-config__head {
    display: none;
  }

  .image-tiered-config__row {
    grid-template-columns: 1fr;
  }
}
</style>
