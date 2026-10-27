<template>
  <div class="res-table">
    <div class="res-table__head">
      <span>档位名</span>
      <span>无声</span>
      <span>有声</span>
      <span>含参考</span>
      <span>含参考 + 有声</span>
      <span/>
    </div>
    <div v-for="(row, index) in rows" :key="row.uid || `${uidPrefix}-${index}`" class="res-table__row">
      <el-select
        :model-value="row.key"
        filterable
        allow-create
        default-first-option
        placeholder="如 720P / 4K"
        @update:model-value="(value: string) => setKey(index, value)"
      >
        <el-option v-for="item in options" :key="item" :label="item" :value="item"/>
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
    <div class="res-table__actions">
      <el-button @click="append">新增档位</el-button>
      <el-button @click="fillCommon">填入常用档位</el-button>
      <span class="res-table__tip">档位名留空的行不会保存</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import {type PropType} from "vue";
import {Delete} from "@element-plus/icons-vue";
import {UNIT_PRICE_STEP} from "@/utils/billingPrice";
import {createResolutionRow} from "@/utils/billingResolution";
import type {ResolutionPriceField, ResolutionRow} from "@/utils/billingResolution";

/**
 * 「档位 × 有声 × 含参考」五列整表（维度分档价 DIMENSION_TIERED 使用）。
 *
 * <p>⭐ 该表最初从 Kling 方案组件里抽出：维度分档价（DIMENSION_TIERED）用的是<b>同一张表、同一套
 * 键契约</b>（{@code 档位} / {@code 档位:AUDIO} / {@code 档位:REFERENCE} / {@code 档位:REFERENCE:AUDIO}），
 * 再抄一份就会出现「同一档位行有两套创建逻辑」——2026-10-26 加 {@code inputPrice} 时漏一处、
 * 输入价保存后回显不出来的那类缺陷，正是这么来的。表的编辑动作收在这里，
 * 方案组件只决定「要不要这张表」和「旁边放什么口径/参考信息」。</p>
 *
 * <p>行数据仍然是宿主传进来的 {@code form.videoResolutionRows}（真实对象引用），
 * 本组件直接改行字段，不在内部另存一份——否则保存时会互相覆盖。</p>
 */
const props = defineProps({
  rows: {type: Array as PropType<ResolutionRow[]>, required: true},
  /** 常用档位下拉建议：只是建议，不是合法值白名单（厂商档位不可枚举） */
  options: {type: Array as PropType<string[]>, default: (): string[] => ["720P", "1080P", "4K"]},
  /** v-for key 前缀：同一抽屉内两张表并存时避免 key 相撞 */
  uidPrefix: {type: String, default: "res-table"}
});

let sequence = 0;

function nextUid(): string {
  sequence += 1;
  return `${props.uidPrefix}-${Date.now()}-${sequence}`;
}

function append() {
  props.rows.push(createResolutionRow("", nextUid()));
}

function remove(index: number) {
  props.rows.splice(index, 1);
}

function setKey(index: number, value: string) {
  const row = props.rows[index];
  if (row) {
    row.key = String(value || "").trim().toUpperCase();
  }
}

function setPrice(index: number, field: ResolutionPriceField, value: number | undefined | null) {
  const row = props.rows[index];
  if (row) {
    row[field] = value == null ? null : value;
  }
}

/** 一键铺开常用档位（只补不存在的，不覆盖已填价格） */
function fillCommon() {
  const existing = new Set(props.rows.map((row) => row.key.toUpperCase()));
  for (const key of props.options) {
    if (!existing.has(key)) {
      props.rows.push(createResolutionRow(key, nextUid()));
    }
  }
}
</script>

<style scoped>
.res-table {
  width: 100%;
}

.res-table__head,
.res-table__row {
  display: grid;
  grid-template-columns: 1.3fr 1fr 1fr 1fr 1.3fr 34px;
  gap: 8px;
  align-items: center;
}

.res-table__head {
  padding: 0 12px 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.res-table__row {
  padding: 10px 12px;
  margin-bottom: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}

.res-table__row :deep(.el-input-number) {
  width: 100%;
}

.res-table__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.res-table__tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (max-width: 900px) {
  .res-table__head {
    display: none;
  }

  .res-table__row {
    grid-template-columns: 1fr;
  }
}
</style>
