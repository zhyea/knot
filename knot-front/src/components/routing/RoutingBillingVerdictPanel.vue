<template>
  <section class="billing-verdict">
    <div class="bv-head">
      <h3>计费校验</h3>
      <p>
        双盲对照：自检区（算术 · 代码权威）与裁判区（语义 · JEV 辅助）互不可见。
        两区同时亮红可信度更高；仅单边亮红优先相信自检，裁判供语义参考。保留人工判断，不自动阻断、不改账。
      </p>
    </div>

    <div v-if="skipped" class="bv-notice bv-notice--info">
      <strong>本次响应无消费详情，未发起裁判</strong>
      <span>消费者未开启「返回消费详情」，且本次测试未勾选「临时开启」。勾选后重测即可校验计费。</span>
    </div>

    <div v-else-if="loading" class="bv-loading">
      <span class="bv-spinner" aria-hidden="true"></span>
      <span>正在做影子重算与裁判判定…（不阻塞上方测试结果）</span>
    </div>

    <div v-else-if="error" class="bv-notice bv-notice--error">
      <strong>计费校验请求失败</strong>
      <span>{{ error }}</span>
    </div>

    <template v-else-if="result">
      <div v-if="result.shadowError" class="bv-notice bv-notice--warn">
        <strong>影子链未完成，自检与裁判均未执行</strong>
        <span>{{ result.shadowError }}</span>
      </div>

      <!-- 区一：计费事实 -->
      <div class="bv-zone bv-zone--first">
        <div class="bv-zone__head">
          <span class="bv-zone__index">①</span>
          <span class="bv-zone__title">计费事实</span>
          <span class="bv-zone__hint">上游依据 ↔ 网关被告账 ↔ 影子重算</span>
        </div>

        <div class="bv-kv">
          <div class="bv-kv__item">
            <span class="bv-kv__label">命中规则</span>
            <span class="bv-kv__value">{{ result.ruleRef?.code || "未命中" }}</span>
          </div>
          <div class="bv-kv__item">
            <span class="bv-kv__label">命中层级</span>
            <span class="bv-kv__value">{{ result.ruleRef?.matchedBy || "-" }}</span>
          </div>
          <div class="bv-kv__item">
            <span class="bv-kv__label">计费模式</span>
            <span class="bv-kv__value">{{ result.ruleRef?.billingMode || "-" }}</span>
          </div>
          <div class="bv-kv__item">
            <span class="bv-kv__label">计费单位</span>
            <span class="bv-kv__value">{{ result.ruleRef?.unit || "-" }}</span>
          </div>
          <div class="bv-kv__item">
            <span class="bv-kv__label">上游 cost</span>
            <span class="bv-kv__value" :class="{ 'bv-muted': result.upstreamCost == null }">
              {{ result.upstreamCost == null || result.upstreamCost === "" ? "上游未返回" : result.upstreamCost }}
            </span>
          </div>
          <div class="bv-kv__item">
            <span class="bv-kv__label">被告总额</span>
            <span class="bv-kv__value bv-strong">{{ money(result.producedBilling?.totalCost) }}
              <em v-if="result.producedBilling?.currency">{{ result.producedBilling?.currency }}</em>
            </span>
          </div>
        </div>

        <div v-if="usagePairs.length" class="bv-table-wrap">
          <div class="bv-table__caption">用量对照（上游原文 ↔ 网关归一化）</div>
          <table class="bv-table">
            <thead>
            <tr>
              <th>字段</th>
              <th class="bv-num">上游原文</th>
              <th class="bv-num">knot_extend.usage</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="row in usagePairs" :key="row.key">
              <td>{{ row.key }}</td>
              <td class="bv-num">{{ row.upstream }}</td>
              <td class="bv-num">{{ row.produced }}</td>
            </tr>
            </tbody>
          </table>
        </div>

        <div class="bv-table-wrap">
          <div class="bv-table__caption">被告账明细（knot_extend.billing）</div>
          <table class="bv-table">
            <thead>
            <tr>
              <th>明细类型</th>
              <th class="bv-num">计费量</th>
              <th class="bv-num">单价 / 百万</th>
              <th class="bv-num">被告金额</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="(line, index) in producedLines" :key="index">
              <td>{{ line.type || "-" }}</td>
              <td class="bv-num">{{ line.tokens ?? 0 }}</td>
              <td class="bv-num">{{ line.price || "-" }}</td>
              <td class="bv-num">{{ line.cost || "-" }}</td>
            </tr>
            <tr v-if="!producedLines.length">
              <td colspan="4" class="bv-empty">无计费明细</td>
            </tr>
            </tbody>
          </table>
        </div>

        <div v-if="shadowLines.length" class="bv-table-wrap">
          <div class="bv-table__caption">影子重算明细（离线复算，仅本区展示）</div>
          <table class="bv-table">
            <thead>
            <tr>
              <th>明细类型</th>
              <th class="bv-num">计费量</th>
              <th class="bv-num">单价 / 百万</th>
              <th class="bv-num">重算金额</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="(line, index) in shadowLines" :key="index">
              <td>{{ line.type || "-" }}</td>
              <td class="bv-num">{{ line.tokens ?? 0 }}</td>
              <td class="bv-num">{{ line.price || "-" }}</td>
              <td class="bv-num">{{ line.cost || "-" }}</td>
            </tr>
            </tbody>
          </table>
        </div>

        <div class="bv-shadow">
          影子重算总额：<b>{{ money(result.shadowBilling?.totalCost) }}</b>
          <span v-if="shadowDiffText" class="bv-shadow__diff">{{ shadowDiffText }}</span>
        </div>
      </div>

      <!-- 区二：自检七项 -->
      <div class="bv-zone">
        <div class="bv-zone__head">
          <span class="bv-zone__index">②</span>
          <span class="bv-zone__title">自检（七项）</span>
          <span class="bv-zone__hint">算术正确性 · 代码精确 · SKIP 不充 PASS</span>
        </div>
        <ul class="bv-checks">
          <li
            v-for="check in result.checks"
            :key="check.code"
            class="bv-check"
            :class="`bv-check--${String(check.status).toLowerCase()}`"
          >
            <span class="bv-check__badge">{{ check.status }}</span>
            <span class="bv-check__code">{{ checkLabel(check.code) }}</span>
            <span v-if="check.message" class="bv-check__msg">{{ check.message }}</span>
          </li>
          <li v-if="!result.checks?.length" class="bv-empty">自检未执行</li>
        </ul>
      </div>

      <!-- 区三：裁判结论 -->
      <div class="bv-zone">
        <div class="bv-zone__head">
          <span class="bv-zone__index">③</span>
          <span class="bv-zone__title">裁判结论（JEV · 语义）</span>
          <span class="bv-zone__hint">未看自检结论 · 辅助非终裁</span>
        </div>

        <div v-if="!result.verdict?.available" class="bv-notice bv-notice--warn">
          <strong>裁判未返回结论</strong>
          <span>{{ result.verdict?.reason || "裁判不可用" }}</span>
        </div>
        <template v-else>
          <p v-if="result.verdict.summary" class="bv-summary">{{ result.verdict.summary }}</p>
          <div class="bv-answers">
            <div
              v-for="(answer, answerIndex) in result.verdict.answers || []"
              :key="answer.key ?? answerIndex"
              class="bv-answer"
            >
              <div class="bv-answer__head">
                <span class="bv-answer__key">{{ answerLabel(answer.key) }}</span>
                <span class="bv-answer__status" :class="{ 'bv-muted': answer.status === 'NOT_RETURNED' }">
                  {{ answer.status === "NOT_RETURNED" ? "未返回" : "已返回" }}
                </span>
              </div>
              <div class="bv-answer__value">{{ answerText(answer) }}</div>
              <div v-if="answer.note" class="bv-answer__note">{{ answer.note }}</div>
            </div>
            <div v-if="!result.verdict.answers?.length" class="bv-empty">裁判未给出答案</div>
          </div>
        </template>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import {computed} from "vue";
import type {
  BillingCheckCode,
  BillingFactAmountLine,
  JevAnswerItem,
  RoutingBillingVerdictResult
} from "@/api/routing";

const props = defineProps<{
  result: RoutingBillingVerdictResult | null;
  loading: boolean;
  /** 本次响应无 knot_extend：链路短路，未发起裁判 */
  skipped: boolean;
  error: string;
}>();

const producedLines = computed<BillingFactAmountLine[]>(
  () => props.result?.producedBilling?.details ?? []
);

/** 影子重算明细（「重算算式摊开」）：仅本区展示，不喂 JEV */
const shadowLines = computed<BillingFactAmountLine[]>(
  () => props.result?.shadowBilling?.details ?? []
);

/** 用量对照：上游原文 usage ↔ 网关归一化 knot_extend.usage，按字段并集对齐 */
const usagePairs = computed<Array<{ key: string; upstream: string; produced: string }>>(() => {
  const upstream = (props.result?.upstreamUsage ?? {}) as Record<string, unknown>;
  const produced = (props.result?.producedUsage ?? {}) as Record<string, unknown>;
  const keys = new Set<string>([...Object.keys(upstream), ...Object.keys(produced)]);
  return [...keys]
    .sort()
    .map((key) => ({
      key,
      upstream: formatValue(upstream[key]),
      produced: formatValue(produced[key]),
    }));
});

function formatValue(value: unknown): string {
  if (value == null || value === "") {
    return "-";
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}

/** 影子重算与被告账的差额说明（仅数值可解析时给出） */
const shadowDiffText = computed(() => {
  const produced = props.result?.producedBilling?.totalCost;
  const shadow = props.result?.shadowBilling?.totalCost;
  if (produced == null || produced === "" || shadow == null || shadow === "") {
    return "";
  }
  const p = Number(produced);
  const s = Number(shadow);
  if (!Number.isFinite(p) || !Number.isFinite(s)) {
    return "";
  }
  const diff = Math.abs(p - s);
  if (diff === 0) {
    return "与被告账一致";
  }
  return `与被告账差 ${formatNumber(diff)}`;
});

const CHECK_LABELS: Record<BillingCheckCode, string> = {
  USAGE_NOT_EMPTY: "用量非空",
  TOTAL_TOKENS_CONSISTENT: "总量一致",
  COST_FORMULA_MATCH: "金额公式",
  GATEWAY_RECOMPUTE_MATCH: "被告 = 重算",
  RULE_MODE_MATCH: "模式匹配",
  RULE_SOURCE_EXPECTED: "规则来源",
  UPSTREAM_COST_DEVIATION: "上游偏离",
};

const ANSWER_LABELS: Record<string, string> = {
  usage_parse_ok: "用量解析是否完整正确",
  rule_match_ok: "命中规则是否合适",
  cost_plausible: "金额与依据是否相称",
  risk_level: "综合风险刻度",
};

function checkLabel(code?: string | null): string {
  if (!code) {
    return "-";
  }
  return CHECK_LABELS[code as BillingCheckCode] || code;
}

function answerLabel(key?: string | null): string {
  if (!key) {
    return "-";
  }
  return ANSWER_LABELS[key] || key;
}

function money(value?: string | null): string {
  return value == null || value === "" ? "-" : value;
}

function formatNumber(value: number): string {
  if (value === 0) {
    return "0";
  }
  return String(Number(value.toPrecision(6)));
}

function answerText(answer: JevAnswerItem): string {
  if (answer.status === "NOT_RETURNED") {
    return "未返回";
  }
  const value = answer.value;
  if (value == null) {
    return "-";
  }
  if (answer.type === "noul") {
    if (value === true) {
      return "是（true）";
    }
    if (value === false) {
      return "否（false）";
    }
    return String(value);
  }
  if (answer.type === "score") {
    const confidence = answer.confidence != null ? ` · 置信 ${percent(answer.confidence)}` : "";
    const legend = answer.legend?.length ? ` · ${answer.legend.join(" / ")}` : "";
    return `${value}${confidence}${legend}`;
  }
  // choice：选中项 + 置信度（缺失标「无置信度」）
  const confidence = answer.confidence != null ? ` · 置信 ${percent(answer.confidence)}` : " · 无置信度";
  return `${value}${confidence}`;
}

function percent(value: number): string {
  return `${(value * 100).toFixed(0)}%`;
}
</script>

<style scoped>
.billing-verdict {
  margin-top: 16px;
  padding: 16px 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.bv-head h3 {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.bv-head p {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.bv-notice {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.6;
}

.bv-notice strong {
  font-weight: 600;
}

.bv-notice span {
  color: var(--el-text-color-regular);
}

.bv-notice--info {
  border: 1px solid var(--el-color-info-light-7);
  background: var(--el-color-info-light-9);
}

.bv-notice--warn {
  border: 1px solid var(--el-color-warning-light-7);
  background: var(--el-color-warning-light-9);
}

.bv-notice--error {
  border: 1px solid var(--el-color-danger-light-7);
  background: var(--el-color-danger-light-9);
}

.bv-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 4px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.bv-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--el-border-color);
  border-top-color: var(--el-color-primary);
  border-radius: 50%;
  animation: bv-spin 0.8s linear infinite;
}

@keyframes bv-spin {
  to {
    transform: rotate(360deg);
  }
}

.bv-zone {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.bv-zone--first {
  margin-top: 6px;
}

.bv-zone__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 10px;
}

.bv-zone__index {
  font-weight: 700;
  color: var(--el-color-primary);
}

.bv-zone__title {
  font-size: var(--el-font-size-base, 13px);
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.bv-zone__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.bv-kv {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 8px 16px;
  margin-bottom: 12px;
}

.bv-kv__item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
}

.bv-kv__label {
  flex: 0 0 auto;
  color: var(--el-text-color-secondary);
}

.bv-kv__value {
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.bv-kv__value em {
  margin-left: 4px;
  font-style: normal;
  color: var(--el-text-color-secondary);
}

.bv-strong {
  font-weight: 600;
}

.bv-muted {
  color: var(--el-text-color-placeholder);
}

.bv-table-wrap {
  overflow-x: auto;
}

.bv-table-wrap + .bv-table-wrap {
  margin-top: 10px;
}

.bv-table__caption {
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.bv-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.bv-table th,
.bv-table td {
  padding: 6px 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  text-align: left;
  color: var(--el-text-color-regular);
}

.bv-table th {
  font-weight: 600;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-lighter);
}

.bv-table .bv-num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.bv-empty {
  padding: 10px;
  text-align: center;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}

.bv-shadow {
  margin-top: 10px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.bv-shadow__diff {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}

.bv-checks {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.bv-check {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 13px;
  line-height: 1.5;
}

.bv-check__badge {
  flex: 0 0 auto;
  min-width: 44px;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  text-align: center;
}

.bv-check--pass .bv-check__badge {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.bv-check--fail .bv-check__badge {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.bv-check--skip .bv-check__badge {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.bv-check__code {
  flex: 0 0 120px;
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.bv-check__msg {
  color: var(--el-text-color-regular);
  word-break: break-all;
}

.bv-summary {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
}

.bv-answers {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.bv-answer {
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}

.bv-answer__head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.bv-answer__key {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.bv-answer__value {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.bv-answer__note {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
