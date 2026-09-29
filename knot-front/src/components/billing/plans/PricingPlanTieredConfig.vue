<template>
  <div class="tier-editor">
    <el-alert type="info" :closable="false" class="tier-editor__hint">
      <template #title>
        <div class="tier-editor__hint-body">
          <span>
            按{{ scope.amountLabel }}命中<strong>唯一档位</strong>计价，不跨档拆段；未命中任何档位时回退上方基础价。
            起始用量与截止用量均为闭区间，截止用量留空 = 上不封顶，<strong>该档只能是最后一档</strong>。
          </span>
          <span class="tier-editor__hint-unit">阶梯档位基准：{{ scope.amountLabel }}</span>
        </div>
      </template>
    </el-alert>

    <div v-for="(row, index) in rows" :key="row.uid || `tier-${index}`" class="tier-card">
      <div class="tier-card__head">
        <span class="tier-card__seq">第 {{ index + 1 }} 档</span>
        <span class="tier-card__range">{{ describeTierRange(row) }}</span>
        <span class="tier-card__spacer"/>
        <el-button link :disabled="index === 0" @click="move(index, -1)">上移</el-button>
        <el-button link :disabled="index === rows.length - 1" @click="move(index, 1)">下移</el-button>
        <el-button link type="danger" @click="remove(index)">删除</el-button>
      </div>

      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="起始用量" :error="fieldError(index, 'from')">
            <el-input-number
              :model-value="row.from"
              :min="0"
              :step="1000"
              :controls="false"
              size="small"
              placeholder="必填（含）"
              class="tier-card__input"
              @update:model-value="(value) => setValue(index, 'from', value)"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="截止用量" :error="fieldError(index, 'to')">
            <el-input-number
              :model-value="row.to"
              :min="0"
              :step="1000"
              :controls="false"
              size="small"
              placeholder="留空 = 上不封顶（含）"
              class="tier-card__input"
              @update:model-value="(value) => setValue(index, 'to', value)"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="档位操作">
            <el-button size="small" :disabled="!canFillFromBase" @click="fillTierFromBase(index)">
              填基础价
            </el-button>
          </el-form-item>
        </el-col>
      </el-row>

      <TierPriceFields
        :index="index"
        :prices="row.unitPrices"
        :issues="issues"
        @update:prices="(value) => setPrices(index, value)"
      />
    </div>

    <div class="tier-editor__actions">
      <el-button :disabled="!canAppend" @click="append">新增档位</el-button>
      <el-button :disabled="!rows.length || !canFillFromBase" @click="fillAllFromBase">全部填入基础价</el-button>
      <span v-if="!canAppend" class="tier-editor__tip">末档已上不封顶，需先补齐截止用量才能继续加档</span>
    </div>

    <div v-if="issues.length" class="tier-editor__issues">
      <div v-for="(issue, i) in issues" :key="`${issue.index}-${issue.field}-${i}`" class="tier-editor__issue">
        {{ issue.message }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {ElMessage} from "element-plus";
import type {Dict} from "@/types";
import TierPriceFields from "./TierPriceFields.vue";
import {
  TIER_PRICE_FIELDS,
  basePricesOf,
  createTierPriceSet,
  createTierRow,
  describeTierRange,
  toNumberOrNull,
  issueMessage,
  tierScope,
  validateTierRows
} from "@/utils/billingTier";
import type {TierIssue, TierPriceSet, TierRow} from "@/utils/billingTier";

const props = defineProps({
  form: {type: Object as () => Dict, required: true}
});

/**
 * 档位数据直接落在父表单的 tiers 数组上（与其余模式/方案组件一致：共享同一份 reactive form）。
 * 校验实时计算，父组件保存前用同一份 {@link validateTierRows} 结果阻断提交。
 */
const rows = computed<TierRow[]>(() => (Array.isArray(props.form.tiers) ? props.form.tiers : []));
const issues = computed<TierIssue[]>(() => validateTierRows(rows.value));
/** 用量口径与基础价映射由计费模式决定；方案层不感知具体模式 */
const scope = computed(() => tierScope(props.form.billingMode));
const canFillFromBase = computed(() => scope.value.basePriceFields != null);
const canAppend = computed(() => {
  const last = rows.value[rows.value.length - 1];
  return !last || toNumberOrNull(last.to) != null;
});

function setValue(index: number, key: "from" | "to", value: number | undefined | null) {
  const row = rows.value[index];
  if (!row) {
    return;
  }
  row[key] = toNumberOrNull(value);
}

function setPrices(index: number, prices: TierPriceSet) {
  const row = rows.value[index];
  if (!row) {
    return;
  }
  row.unitPrices = prices;
}

/** 新档起点 = 上一档截止用量 + 1，区间连续不重叠 */
function append() {
  const last = rows.value[rows.value.length - 1];
  const previousTo = last ? toNumberOrNull(last.to) : null;
  const row = createTierRow({
    from: previousTo == null ? 0 : previousTo + 1,
    unitPrices: basePrices() || createTierPriceSet(null)
  });
  rows.value.push(row);
}

function remove(index: number) {
  rows.value.splice(index, 1);
}

function move(index: number, offset: number) {
  const target = index + offset;
  if (target < 0 || target >= rows.value.length) {
    return;
  }
  const list = rows.value;
  const [moved] = list.splice(index, 1);
  list.splice(target, 0, moved);
}

/** 取当前模式的基础单价（由 mode 的 scope 决定，本组件不直接读模式专属字段） */
function basePrices(): TierPriceSet | null {
  return basePricesOf(props.form, props.form.billingMode);
}

function fillTierFromBase(index: number) {
  const row = rows.value[index];
  const base = basePrices();
  if (!row || !base) {
    return;
  }
  row.unitPrices = base;
  ElMessage.success(`第 ${index + 1} 档已填入基础价`);
}

function fillAllFromBase() {
  const base = basePrices();
  if (!base) {
    ElMessage.warning("当前计费模式没有分项基础价，请直接填写各档单价");
    return;
  }
  const missing = TIER_PRICE_FIELDS.filter((field) => base[field.key] == null);
  if (missing.length) {
    ElMessage.warning(`基础价缺少 ${missing.map((field) => field.label).join("、")}，请先在上方填写`);
    return;
  }
  rows.value.forEach((row) => {
    row.unitPrices = {...base};
  });
  ElMessage.success("全部档位已填入基础价");
}

function fieldError(index: number, field: TierIssue["field"]): string {
  return issueMessage(issues.value, index, field);
}
</script>

<style scoped>
.tier-editor {
  width: 100%;
  margin-top: 4px;
}

.tier-editor__hint {
  margin-bottom: 12px;
}

.tier-editor__hint-body {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 12px;
  font-size: 13px;
  line-height: 20px;
}

.tier-editor__hint-unit {
  color: var(--el-color-info);
  font-size: 12px;
}

.tier-card {
  margin-bottom: 12px;
  padding: 12px 14px 4px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}

.tier-card__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.tier-card__seq {
  font-size: 14px;
  font-weight: 600;
}

.tier-card__range {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.tier-card__spacer {
  flex: 1;
}

.tier-card__input {
  width: 100%;
}

.tier-editor__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.tier-editor__tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.tier-editor__issues {
  margin-top: 10px;
  padding: 8px 10px;
  border: 1px solid var(--el-color-danger-light-7);
  border-radius: 4px;
  background: var(--el-color-danger-light-9);
}

.tier-editor__issue {
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 20px;
}
</style>
