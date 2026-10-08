<template>
  <div class="peak-editor">
    <el-alert type="info" :closable="false" class="peak-editor__hint">
      <template #title>
        <div class="peak-editor__hint-body">
          <span v-if="isAbsolute">
            高低峰只描述「价」怎么随时间变化：<strong>ABSOLUTE 模式下最终单价 = 命中位相自身携带的单价</strong>，
            不再对上方基础价做乘法。时段按所选 <strong>UTC 偏移</strong> 解释；首期节假日日历为空，不会命中。
          </span>
          <span v-else>
            高低峰只描述「价」怎么随时间变化：<strong>最终单价 = 上方基础价 × 相位倍率</strong>，
            方案层不持有任何价格。时段按所选 <strong>UTC 偏移</strong> 解释；首期节假日日历为空，不会命中。
          </span>
        </div>
      </template>
    </el-alert>

    <el-row :gutter="16">
      <el-col :span="8">
        <el-form-item label="时区" class="billing-usage-field">
          <el-select v-model="pricing.timezone" filterable class="peak-editor__control">
            <el-option
              v-for="item in PEAK_TIMEZONE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="16">
        <el-form-item label-width="0">
          <span class="peak-editor__tip">请选择 UTC 偏移；跨午夜时段（如 22:00–02:00）请拆成两段配置。</span>
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="24">
        <el-form-item label="计价方式" class="billing-usage-field">
          <el-radio-group v-model="pricing.rateMode">
            <el-radio-button value="MULTIPLIER">倍数（基础价 × 倍率）</el-radio-button>
            <el-radio-button value="ABSOLUTE">独立单价（命中位相自身单价）</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-col>
      <el-col v-if="isAbsolute" :span="24">
        <el-form-item label-width="0">
          <span class="peak-editor__tip">
            {{
              isTokenMode
                ? "ABSOLUTE 模式每个相位需填写 6 项分项单价（输入/输出/缓存读/缓存写/缓存写5m/缓存写1h）。"
                : "ABSOLUTE 模式每个相位只需填写输入单价，最终单价不再对基础价做乘法。"
            }}
          </span>
        </el-form-item>
      </el-col>
    </el-row>

    <div v-for="(row, index) in peakPhases" :key="row.uid || `phase-${index}`" class="phase-card">
      <div class="phase-card__head">
        <span class="phase-card__title">高峰规则 {{ index + 1 }}</span>
        <span class="phase-card__summary">{{ phaseSummary(row) }}</span>
        <span class="phase-card__spacer"/>
        <el-button link type="danger" :disabled="peakPhases.length <= 1" @click="removePhase(index)">
          删除
        </el-button>
      </div>

      <el-row v-if="!isAbsolute" :gutter="12">
        <el-col :span="8">
          <el-form-item label="高峰倍率" :error="fieldError(index, 'multiplier')">
            <el-input-number
              v-model="row.multiplier"
              :min="0"
              :max="1"
              :step="0.05"
              :precision="2"
              :controls="false"
              size="small"
              placeholder="0 < 倍率 <= 1"
              class="phase-card__control"
            />
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="适用星期" :error="fieldError(index, 'weekdays')">
            <el-checkbox-group v-model="row.weekdays" size="small">
              <el-checkbox-button v-for="day in PEAK_WEEKDAYS" :key="day.value" :value="day.value">
                {{ day.short }}
              </el-checkbox-button>
            </el-checkbox-group>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row v-else :gutter="12">
        <el-col :span="24">
          <el-form-item label="相位单价" :error="absFieldError(index)">
            <div class="phase-card__unit-prices">
              <div v-for="field in unitPriceFields" :key="field.key" class="unit-price-item">
                <span class="unit-price-item__label">{{ field.label }}</span>
                <el-input-number
                  :model-value="row.unitPrices?.[field.key] ?? null"
                  :min="0"
                  :step="0.0001"
                  :controls="false"
                  size="small"
                  placeholder="0"
                  class="unit-price-item__control"
                  @update:model-value="(v: number | null | undefined) => setUnitPrice(row, field.key, v)"
                />
              </div>
            </div>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row v-if="isAbsolute" :gutter="12">
        <el-col :span="16">
          <el-form-item label="适用星期" :error="fieldError(index, 'weekdays')">
            <el-checkbox-group v-model="row.weekdays" size="small">
              <el-checkbox-button v-for="day in PEAK_WEEKDAYS" :key="day.value" :value="day.value">
                {{ day.short }}
              </el-checkbox-button>
            </el-checkbox-group>
          </el-form-item>
        </el-col>
      </el-row>

      <div class="phase-card__windows">
        <div v-for="(window, windowIndex) in row.windows" :key="window.uid || `w-${windowIndex}`" class="window-row">
          <el-form-item label="时段" :error="windowError(index, windowIndex)">
            <div class="window-row__inputs">
              <el-time-picker
                v-model="window.start"
                format="HH:mm"
                value-format="HH:mm"
                placeholder="开始（含）"
                size="small"
                class="window-row__time"
              />
              <span class="window-row__sep">至</span>
              <el-time-picker
                v-if="!isMidnightEnd(window)"
                v-model="window.end"
                format="HH:mm"
                value-format="HH:mm"
                placeholder="结束（不含）"
                size="small"
                class="window-row__time"
              />
              <span v-else class="window-row__midnight">24:00（午夜，次日 0 点）</span>
              <el-checkbox
                :model-value="isMidnightEnd(window)"
                size="small"
                class="window-row__to-midnight"
                @change="(checked) => toggleMidnightEnd(window, checked)"
              >
                至午夜
              </el-checkbox>
              <el-button link type="danger" @click="removeWindow(index, windowIndex)">删除</el-button>
            </div>
          </el-form-item>
        </div>
        <div class="phase-card__window-actions">
          <el-button size="small" @click="addWindow(index)">新增时段</el-button>
          <span class="peak-editor__tip">
            左闭右开：01:00 命中 01:00–04:00，04:00 不命中；「至午夜」表示覆盖到当日 24:00（= 次日 0 点）
          </span>
        </div>
      </div>

      <el-form-item label-width="0">
        <span class="peak-editor__tip">节假日整日按低峰计费；历史调休配置仍可兼容读取。</span>
      </el-form-item>
    </div>

    <div class="peak-editor__actions">
      <el-button size="small" @click="addPhase">新增高峰规则</el-button>
      <span class="peak-editor__tip">多条高峰规则按顺序匹配，命中第一条即生效</span>
    </div>

    <div class="phase-card phase-card--fallback">
      <div class="phase-card__head">
        <span class="phase-card__title">兜底低峰</span>
        <span class="phase-card__summary">未命中任何高峰时段时生效</span>
      </div>
      <el-row v-if="!isAbsolute" :gutter="12">
        <el-col :span="8">
          <el-form-item label="低峰倍率" :error="fieldError(fallbackIndex, 'multiplier')">
            <el-input-number
              v-model="fallback.multiplier"
              :min="0"
              :max="1"
              :step="0.05"
              :precision="2"
              :controls="false"
              size="small"
              placeholder="0 < 倍率 <= 1"
              class="phase-card__control"
            />
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label-width="0">
            <span class="peak-editor__tip">
              末条规则必须为兜底低峰（condition.type=DEFAULT），后端保存时会复核
            </span>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row v-else :gutter="12">
        <el-col :span="24">
          <el-form-item label="低峰单价" :error="absFieldError(fallbackIndex)">
            <div class="phase-card__unit-prices">
              <div v-for="field in unitPriceFields" :key="field.key" class="unit-price-item">
                <span class="unit-price-item__label">{{ field.label }}</span>
                <el-input-number
                  :model-value="fallback.unitPrices?.[field.key] ?? null"
                  :min="0"
                  :step="0.0001"
                  :controls="false"
                  size="small"
                  placeholder="0"
                  class="unit-price-item__control"
                  @update:model-value="(v: number | null | undefined) => setUnitPrice(fallback, field.key, v)"
                />
              </div>
            </div>
          </el-form-item>
        </el-col>
      </el-row>
    </div>

    <div class="peak-timeline">
      <div class="peak-timeline__head">
        <span class="peak-timeline__label">一周时间轴</span>
        <span class="peak-editor__tip">高峰时段（{{ pricing.timezone }}）</span>
      </div>
      <div v-for="day in PEAK_WEEKDAYS" :key="day.value" class="peak-timeline__row">
        <span class="peak-timeline__day">{{ day.label }}</span>
        <div class="peak-timeline__track">
          <span
            v-for="hour in 24"
            :key="hour"
            class="peak-timeline__cell"
            :class="{ 'peak-timeline__cell--peak': isPeakHour(day.value, hour - 1) }"
            :title="`${day.label} ${String(hour - 1).padStart(2, '0')}:00`"
          />
        </div>
      </div>
      <div class="peak-timeline__axis">
        <span class="peak-timeline__day"/>
        <div class="peak-timeline__track peak-timeline__track--axis">
          <span class="peak-timeline__tick">00</span>
          <span class="peak-timeline__tick">06</span>
          <span class="peak-timeline__tick">12</span>
          <span class="peak-timeline__tick">18</span>
          <span class="peak-timeline__tick peak-timeline__tick--end">24</span>
        </div>
      </div>
    </div>

    <div class="peak-probe">
      <div class="peak-probe__head">
        <span class="peak-timeline__label">时间判定测试</span>
        <span class="peak-editor__tip">本地按同一套规则试算，保存后可用 /api/billing/rules/{id}/preview 复核</span>
      </div>
      <div class="peak-probe__body">
        <el-date-picker
          v-model="probeDate"
          type="date"
          value-format="YYYY-MM-DD"
          :placeholder="`${pricing.timezone} 日期`"
          size="small"
          class="peak-probe__date"
        />
        <el-time-picker
          v-model="probeTime"
          format="HH:mm"
          value-format="HH:mm"
          :placeholder="`${pricing.timezone} 时刻`"
          size="small"
          class="peak-probe__time"
        />
        <el-tag v-if="decision" :type="decision.phase === 'PEAK' ? 'warning' : 'success'" size="small">
          <template v-if="isAbsolute && decision.unitPrices">
            {{ PEAK_PHASE_LABELS[decision.phase] }} · {{ absolutePreview(decision.unitPrices) }}
          </template>
          <template v-else>
            {{ PEAK_PHASE_LABELS[decision.phase] }} × {{ decision.multiplier }}
          </template>
        </el-tag>
        <span v-if="decision" class="peak-editor__tip">{{ PEAK_REASONS[decision.reason] }}</span>
      </div>
    </div>

    <div v-if="issues.length" class="peak-editor__issues">
      <div v-for="(issue, index) in issues" :key="`${issue.index}-${issue.field}-${index}`" class="peak-editor__issue">
        {{ issue.message }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, ref, watch} from "vue";
import type {Dict} from "@/types";
import {
  PEAK_MIDNIGHT_END,
  PEAK_PHASE_LABELS,
  PEAK_REASONS,
  PEAK_TIMEZONE_OPTIONS,
  PEAK_UNIT_PRICE_FIELDS,
  PEAK_WEEKDAYS,
  createDefaultPeakPricing,
  createPeakPhaseRow,
  createWindow,
  coversHour,
  coversWeekday,
  describePeakWindows,
  formatClock,
  nextWindowStart,
  parseClockEndpoint,
  peakIssueMessage,
  resolvePeakPhase,
  toNumberOrNull,
  validatePeakPricing
} from "@/utils/billingPeakOffPeak";
import type {PeakIssue, PeakPhaseRow, PeakPricing, PeakUnitPrice, PeakWindow} from "@/utils/billingPeakOffPeak";

const props = defineProps({
  form: {type: Object as () => Dict, required: true}
});

/**
 * 与其余方案组件一致：直接读写父表单上的 peakPricing（同一份 reactive form）。
 * 父组件 resetForm 已负责还原；这里仅在结构缺失时补一份默认骨架，保证组件可独立渲染。
 */
if (!isUsable(props.form.peakPricing)) {
  props.form.peakPricing = createDefaultPeakPricing();
}
const pricing = computed<PeakPricing>(() => props.form.peakPricing as PeakPricing);

function isUsable(value: unknown): boolean {
  const current = value as PeakPricing | null;
  return !!current && Array.isArray(current.phases) && current.phases.length > 0;
}

const issues = computed<PeakIssue[]>(() => validatePeakPricing(pricing.value, props.form.billingMode));
/** ABSOLUTE 模式：展示命中位相自身单价，倍率概念不适用 */
const isAbsolute = computed(() => pricing.value.rateMode === "ABSOLUTE");
/** 当前计费模式：TOKEN 要求 6 项分项单价，简单模式只要求 input */
const isTokenMode = computed(() => props.form.billingMode === "TOKEN");
/** ABSOLUTE 模式下要展示/校验的单价字段集（顺序即展示顺序） */
const unitPriceFields = computed(() =>
  isTokenMode.value ? PEAK_UNIT_PRICE_FIELDS : [PEAK_UNIT_PRICE_FIELDS[0]]
);
/** 高峰规则（非兜底项）按原顺序展示；兜底项单独渲染在下方 */
const peakPhases = computed<PeakPhaseRow[]>(() => pricing.value.phases.filter((row) => !row.isDefault));
const fallbackIndex = computed(() => pricing.value.phases.length - 1);
const fallback = computed<PeakPhaseRow>(() => pricing.value.phases[fallbackIndex.value]);

const probeDate = ref<string>(new Date().toISOString().slice(0, 10));
const probeTime = ref<string>("02:00");
const decision = computed(() => resolvePeakPhase(pricing.value, probeDate.value, probeTime.value));

function phaseSummary(row: PeakPhaseRow): string {
  if (!row.weekdays.length) {
    return "未选星期";
  }
  const labels = PEAK_WEEKDAYS.filter((day) => row.weekdays.includes(day.value)).map((day) => day.short);
  return `${labels.join("")} · ${describePeakWindows(row)}`;
}

/** 时间轴：任一高峰规则在该星期该小时生效即高亮 */
function isPeakHour(day: number, hour: number): boolean {
  return peakPhases.value.some((row) => coversWeekday(row, day) && coversHour(row, hour));
}

/** 新高峰规则插在兜底低峰之前，末项始终是兜底项 */
function addPhase() {
  const phases = pricing.value.phases;
  phases.splice(Math.max(0, phases.length - 1), 0, createPeakPhaseRow({
    multiplier: 1,
    weekdays: [1],
    windows: [createWindow("09:00", "18:00")]
  }));
}

function removePhase(index: number) {
  const row = peakPhases.value[index];
  const at = pricing.value.phases.indexOf(row);
  if (at >= 0) {
    pricing.value.phases.splice(at, 1);
  }
}

/** 该时段是否用了「至午夜」写法（end=24:00） */
function isMidnightEnd(window: PeakWindow): boolean {
  return String(window.end ?? "").trim() === PEAK_MIDNIGHT_END;
}

/** 勾选/取消「至午夜」：勾上写 24:00，取消回到当日最后一个可选时刻 */
function toggleMidnightEnd(window: PeakWindow, checked: unknown) {
  if (checked) {
    window.end = PEAK_MIDNIGHT_END;
    return;
  }
  const start = parseClockEndpoint(window.start);
  window.end = formatClock(Math.min((start ?? 0) + 60, 23 * 60 + 59));
}

/** 新增时段默认接在上一时段之后（可延伸到 24:00），避免随手就填出重叠 */
function addWindow(index: number) {
  const row = peakPhases.value[index];
  const last = row.windows[row.windows.length - 1];
  const startMinute = parseClockEndpoint(nextWindowStart(last?.end));
  const start = startMinute == null ? 9 * 60 : startMinute;
  if (start >= 23 * 60 + 59) {
    row.windows.push(createWindow(formatClock(start), PEAK_MIDNIGHT_END));
    return;
  }
  row.windows.push(createWindow(formatClock(start), formatClock(Math.min(start + 60, 23 * 60 + 59))));
}

function removeWindow(index: number, windowIndex: number) {
  peakPhases.value[index].windows.splice(windowIndex, 1);
}

function fieldError(index: number, field: string): string {
  return peakIssueMessage(issues.value, index, field);
}

/** ABSOLUTE 模式：一个相位的单价错误可能有多个字段，汇总展示首条 */
function absFieldError(index: number): string {
  const matched = issues.value.filter(
    (item) => item.index === index && item.field != null && item.field.startsWith("unitPrices.")
  );
  return matched.length ? matched[0].message : "";
}

/** 探针预览：ABSOLUTE 命中相位的单价文案 */
function absolutePreview(unitPrices: PeakUnitPrice | null | undefined): string {
  if (!unitPrices) {
    return "单价未填";
  }
  const input = toNumberOrNull(unitPrices.input);
  if (input == null) {
    return "单价未填";
  }
  if (isTokenMode.value) {
    const output = toNumberOrNull(unitPrices.output);
    return `输入¥${input}${output != null ? ` / 输出¥${output}` : ""}`;
  }
  return `单价¥${input}`;
}

/**
 * 懒初始化相位单价：unitPrices 为 null 时填空对象再写字段，避免可空 v-model 报错；
 * 清空输入归位为 null，序列化时与「未填写」同口径。
 */
function setUnitPrice(row: PeakPhaseRow, key: keyof PeakUnitPrice, value: number | null | undefined) {
  if (!row.unitPrices) {
    row.unitPrices = {};
  }
  row.unitPrices[key] = value == null ? null : Number(value);
}

function windowError(index: number, windowIndex: number): string {
  return peakIssueMessage(issues.value, index, `windows.${windowIndex}`);
}
</script>

<style scoped>
.peak-editor {
  width: 100%;
  margin-top: 4px;
}

.peak-editor__hint {
  margin-bottom: 12px;
}

.peak-editor__hint-body {
  font-size: 13px;
  line-height: 20px;
}

.peak-editor__control {
  width: 100%;
}

.peak-editor__tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 20px;
}

.phase-card {
  margin-bottom: 12px;
  padding: 12px 14px 4px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}

.phase-card--fallback {
  background: var(--el-fill-color-lighter);
}

.phase-card__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.phase-card__title {
  font-size: 14px;
  font-weight: 600;
}

.phase-card__summary {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.phase-card__spacer {
  flex: 1;
}

.phase-card__control {
  width: 100%;
}

.phase-card__unit-prices {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  width: 100%;
}

.unit-price-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.unit-price-item__label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.unit-price-item__control {
  width: 132px;
}

.phase-card__windows {
  margin-top: 2px;
}

.phase-card__window-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.window-row__inputs {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.window-row__time {
  width: 132px;
}

.window-row__sep {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.window-row__midnight {
  display: inline-flex;
  align-items: center;
  width: 132px;
  height: 24px;
  padding: 0 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-regular);
  font-size: 12px;
}

.window-row__to-midnight {
  margin-right: 4px;
}

.peak-editor__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.peak-timeline {
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.peak-timeline__head,
.peak-probe__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 8px;
}

.peak-timeline__label {
  font-size: 14px;
  font-weight: 600;
}

.peak-timeline__row,
.peak-timeline__axis {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.peak-timeline__day {
  flex: 0 0 40px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.peak-timeline__track {
  display: flex;
  flex: 1;
  gap: 1px;
}

.peak-timeline__track--axis {
  justify-content: space-between;
}

.peak-timeline__cell {
  flex: 1;
  height: 14px;
  border-radius: 2px;
  background: var(--el-fill-color);
}

.peak-timeline__cell--peak {
  background: var(--el-color-warning-light-3);
}

.peak-timeline__tick {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.peak-timeline__tick--end {
  margin-right: -4px;
}

.peak-probe {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.peak-probe__body {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.peak-probe__date {
  width: 150px;
}

.peak-probe__time {
  width: 132px;
}

.peak-editor__issues {
  margin-top: 10px;
  padding: 8px 10px;
  border: 1px solid var(--el-color-danger-light-7);
  border-radius: 4px;
  background: var(--el-color-danger-light-9);
}

.peak-editor__issue {
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 20px;
}
</style>
