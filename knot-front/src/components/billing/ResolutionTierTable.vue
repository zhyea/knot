<template>
  <div class="res-table">
    <div class="res-table__head" :style="gridStyle">
      <span>档位名</span>
      <span v-for="combo in columns" :key="combo.code">{{ combo.label }}</span>
      <span/>
    </div>
    <div v-for="(row, index) in rows" :key="row.uid || `${uidPrefix}-${index}`" class="res-table__row" :style="gridStyle">
      <el-select
        :model-value="row.key"
        filterable
        allow-create
        default-first-option
        placeholder="如 720P / 4K"
        @update:model-value="(value: string) => setKey(index, value)"
      >
        <el-option v-for="item in tierOptions" :key="item" :label="item" :value="item"/>
      </el-select>
      <el-input-number
        v-for="combo in columns"
        :key="combo.code"
        :model-value="row[combo.field]"
        :min="0"
        :step="UNIT_PRICE_STEP"
        controls-position="right"
        :placeholder="combo.placeholder || undefined"
        @update:model-value="(value: number | undefined | null) => setPrice(index, combo.field, value)"
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
import {type PropType, computed, onMounted} from "vue";
import {Delete} from "@element-plus/icons-vue";
import {UNIT_PRICE_STEP} from "@/utils/billingPrice";
import {LEGACY_COMBOS, RESOLUTION_COMBOS, createResolutionRow, normalizeCombos} from "@/utils/billingResolution";
import {useEnums} from "@/composables/useEnums";
import type {ResolutionComboCode, ResolutionPriceField, ResolutionRow} from "@/utils/billingResolution";

/**
 * 「档位 × 计费组合」价格表（视频计费的档位表，通用视频与维度分档价共用）。
 *
 * <p>⭐ 列的组成由 {@code combos} 决定（契约见 utils/billingResolution 的 RESOLUTION_COMBOS）：
 * 勾了哪几个组合就渲染哪几列，没勾的组合连列都不出现。这取代了此前「固定四列硬编码」的做法——
 * 硬编码会让「只要无声 + 含参考」的方案被迫面对两列永远填不上的有声价，
 * 也让「有声/参考」这两列在 FIXED 方案里明明后端取价链认、界面却填不了。</p>
 *
 * <p>行数据是宿主传进来的真实对象（{@code form.videoResolutionRows}），本组件直接改行字段，
 * 不在内部另存一份——否则保存时会互相覆盖。</p>
 */
const props = defineProps({
  rows: {type: Array as PropType<ResolutionRow[]>, required: true},
  /**
   * 要渲染的计费组合；列顺序恒按 RESOLUTION_COMBOS 固定，不随勾选顺序漂移。
   *
   * <p>默认用 {@code LEGACY_COMBOS}（四列全开）而不是新建默认的「仅基础价」：
   * 本组件是纯展示层，宿主不声明列集时保守渲染全部，避免把已填的价格藏起来。</p>
   */
  combos: {type: Array as PropType<ResolutionComboCode[]>, default: () => [...LEGACY_COMBOS]},
  /** v-for key 前缀：同一抽屉内两张表并存时避免 key 相撞 */
  uidPrefix: {type: String, default: "res-table"}
});

/**
 * 档位建议来自 DB 枚举 video_resolution：运营在「枚举配置」界面自己加，加一档不用发版。
 *
 * <p>⚠ 只是建议不是白名单：控件仍 filterable + allow-create，配置侧可填任意档位名。
 * 网关归一不做别名推断，所以档位名必须逐字（除大小写）对上配置键。</p>
 */
const {options: tierRows, loadOptions: loadTierOptions} = useEnums("video_resolution");

const tierOptions = computed<string[]>(() =>
  tierRows.value.map((row) => String(row.itemCode ?? "")).filter((code) => code.length > 0)
);

onMounted(() => {
  // 档位建议拉不到不影响填价：后端归一只做大写，清单不参与匹配，手工输档位名照样能存。
  // 失败就让下拉空着，不向上抛（否则每个挂载本组件的页面都会多一个 unhandled rejection）。
  void loadTierOptions(true).catch(() => undefined);
});

/** 列集：按固定顺序过滤，保证列顺序稳定 */
const columns = computed(() =>
  RESOLUTION_COMBOS.filter((combo) => normalizeCombos(props.combos).includes(combo.code))
);

/** 网格列宽：档位名 1.3fr + 每列 1fr + 删除按钮 34px */
const gridStyle = computed(() => ({
  gridTemplateColumns: `1.3fr repeat(${columns.value.length}, 1fr) 34px`
}));

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
  for (const key of tierOptions.value) {
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
    grid-template-columns: 1fr !important;
  }
}
</style>
