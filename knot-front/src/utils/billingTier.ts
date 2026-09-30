import type {Dict} from "@/types";

/**
 * 计费阶梯（tier）的领域模型：解析、序列化与校验。
 *
 * <p>与后端 {@code org.chobit.knot.gateway.model.BillingConfig} 严格对齐：
 * 档位结构 {@code tier[] = { condition: { from, to }, unitPrices: { 6 项单价 } }}，
 * from 含起点、to 含终点且可空（上不封顶），开放档位只能出现在最后一档；
 * 计费语义为「整笔用量命中唯一档位后取该档单价」，不跨档拆段。
 *
 * <p>前端负责把后端约束前置成可编辑的表单（6 项单价必须齐全、区间不重叠），
 * 后端仍会在 {@code BillingConfig.validate(PricingPlanEnum)} 复核，此处逻辑与其保持一致。
 */

/** 阶梯档位的 6 项单价，与后端 BillingConfig.PriceSet 字段一一对应 */
export interface TierPriceSet {
  input: number | null;
  output: number | null;
  cacheRead: number | null;
  cacheWrite: number | null;
  cacheWrite5m: number | null;
  cacheWrite1h: number | null;
}

/** 单个阶梯档位：命中区间 + 该档完整单价 */
export interface TierRow {
  /** 仅用于前端 v-for 稳定 key，不参与序列化 */
  uid?: string;
  /** 区间起点（含），必填且非负 */
  from: number | null;
  /** 区间终点（含）；null / 空表示上不封顶，仅允许出现在最后一档 */
  to: number | null;
  unitPrices: TierPriceSet;
}

/** 6 项单价的展示定义：顺序即为表单渲染顺序 */
export const TIER_PRICE_FIELDS: ReadonlyArray<{ key: keyof TierPriceSet; label: string; hint: string }> = [
  { key: "input", label: "非缓存输入", hint: "input" },
  { key: "output", label: "输出", hint: "output" },
  { key: "cacheRead", label: "缓存读取", hint: "cacheRead" },
  { key: "cacheWrite", label: "缓存写", hint: "cacheWrite" },
  { key: "cacheWrite5m", label: "缓存写(5m)", hint: "cacheWrite5m" },
  { key: "cacheWrite1h", label: "缓存写(1h)", hint: "cacheWrite1h" }
];

/** 校验结果：定位到档位序号与具体字段 */
export interface TierIssue {
  index: number;
  field?: "from" | "to" | keyof TierPriceSet;
  message: string;
}

/** 宽松转数字：空值与非法值统一归一为 null，避免 0 与未填写无法区分 */
export function toNumberOrNull(value: unknown): number | null {
  if (value == null || value === "") {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

/** 长度为 n 的单价集合；给定基准值时用于「基础价填充到档位」 */
export function createTierPriceSet(fill: number | null = null): TierPriceSet {
  return {
    input: fill,
    output: fill,
    cacheRead: fill,
    cacheWrite: fill,
    cacheWrite5m: fill,
    cacheWrite1h: fill
  };
}

/** 构造空档位 */
export function createTierRow(init: Partial<TierRow> = {}): TierRow {
  return {
    uid: init.uid ?? nextUid(),
    from: init.from ?? null,
    to: init.to ?? null,
    unitPrices: init.unitPrices ? { ...createTierPriceSet(null), ...init.unitPrices } : createTierPriceSet(null)
  };
}

/**
 * 后端 config.tier（数组或 JSON 文本）-> 表单档位数组。
 * 只做形态转换，不做合法性判断；缺字段一律补 null 由 {@link validateTierRows} 提示。
 */
export function parseTierRows(raw: unknown): TierRow[] {
  const list = normalizeArray(raw);
  if (!list.length) {
    return [];
  }
  return list.map((item) => {
    const source = (item && typeof item === "object" ? item : {}) as Dict;
    const condition = (source.condition && typeof source.condition === "object" ? source.condition : {}) as Dict;
    const prices = (source.unitPrices && typeof source.unitPrices === "object" ? source.unitPrices : {}) as Dict;
    return {
      uid: nextUid(),
      from: toNumberOrNull(condition.from),
      to: toNumberOrNull(condition.to),
      unitPrices: {
        input: toNumberOrNull(prices.input),
        output: toNumberOrNull(prices.output),
        cacheRead: toNumberOrNull(prices.cacheRead),
        cacheWrite: toNumberOrNull(prices.cacheWrite),
        cacheWrite5m: toNumberOrNull(prices.cacheWrite5m),
        cacheWrite1h: toNumberOrNull(prices.cacheWrite1h)
      }
    };
  });
}

/** 表单档位数组 -> 后端 config.tier 结构；to 为空时省略该键表达上不封顶 */
export function toTierPayload(rows: TierRow[]): Dict[] {
  return rows.map((row) => {
    const prices: Dict = {};
    for (const field of TIER_PRICE_FIELDS) {
      const value = toNumberOrNull(row.unitPrices?.[field.key]);
      // 后端要求 TIERED 每档 6 项单价齐全：缺失项以 0 落库而非省略
      prices[field.key] = value == null ? 0 : value;
    }
    const to = toNumberOrNull(row.to);
    return {
      condition: {
        from: toNumberOrNull(row.from) ?? 0,
        ...(to == null ? {} : { to })
      },
      unitPrices: prices
    };
  });
}

/**
 * 校验档位配置，返回可读错误列表；空数组表示合法。
 * 逐档校验在前、跨档区间关系校验在后，第一条错误即对应最该修正的位置。
 */
export function validateTierRows(rows: TierRow[]): TierIssue[] {
  if (!rows.length) {
    return [{ index: -1, message: "阶梯价至少需要配置 1 个档位" }];
  }
  const issues: TierIssue[] = [];
  rows.forEach((row, index) => {
    const seq = index + 1;
    const from = toNumberOrNull(row.from);
    const to = toNumberOrNull(row.to);
    if (from == null) {
      issues.push({ index, field: "from", message: `第 ${seq} 档：起始用量必填` });
    } else if (from < 0) {
      issues.push({ index, field: "from", message: `第 ${seq} 档：起始用量不能为负` });
    }
    if (from != null && to != null && from > to) {
      issues.push({ index, field: "to", message: `第 ${seq} 档：起始用量不能大于截止用量` });
    }
    const missing = TIER_PRICE_FIELDS.filter((field) => toNumberOrNull(row.unitPrices?.[field.key]) == null);
    if (missing.length) {
      issues.push({
        index,
        field: missing[0].key,
        message: `第 ${seq} 档：单价必须 6 项齐全，缺少 ${missing.map((field) => field.hint).join("、")}`
      });
    }
    for (const field of TIER_PRICE_FIELDS) {
      const value = toNumberOrNull(row.unitPrices?.[field.key]);
      if (value != null && value < 0) {
        issues.push({ index, field: field.key, message: `第 ${seq} 档：${field.label}单价不能为负` });
        break;
      }
    }
  });

  rows.forEach((row, index) => {
    if (index === 0) {
      return;
    }
    const previous = rows[index - 1];
    const previousTo = toNumberOrNull(previous.to);
    const from = toNumberOrNull(row.from);
    const seq = index + 1;
    if (previousTo == null) {
      issues.push({
        index,
        field: "from",
        message: `第 ${seq} 档：上一档未设截止用量（上不封顶），其后不能再配置档位`
      });
      return;
    }
    if (from != null && from <= previousTo) {
      issues.push({
        index,
        field: "from",
        message: `第 ${seq} 档：起始用量需大于上一档截止用量 ${previousTo}`
      });
    }
  });

  return issues;
}

/** 取指定档位的错误（用于卡片内红字提示） */
export function issuesOf(issues: TierIssue[], index: number): TierIssue[] {
  return issues.filter((issue) => issue.index === index);
}

/** 区间摘要文本：如「0 ~ 1,000,000」/「1,000,001 及以上」 */
export function describeTierRange(row: TierRow): string {
  const from = toNumberOrNull(row.from);
  const to = toNumberOrNull(row.to);
  if (from == null) {
    return "待补全区间";
  }
  if (to == null) {
    return `${formatAmount(from)} 及以上`;
  }
  return `${formatAmount(from)} ~ ${formatAmount(to)}`;
}

/** 数值的千分位展示，保留原有小数位 */
export function formatAmount(value: unknown): string {
  const number = toNumberOrNull(value);
  if (number == null) {
    return "—";
  }
  return number.toLocaleString("en-US", { maximumFractionDigits: 6 });
}

// ==================== 模式 <-> 方案 的衔接 ====================

/**
 * 阶梯所处模式的口径。
 *
 * <p>计费模式决定“量”，进阶定价方案决定“价”，两者互相独立：方案层只关心
 * 「用量怎么读、每档单价怎么填」，不关心自己挂在哪个模式上；模式层也不感知是否存在阶梯。
 *
 * <p>两者的衔接点集中在这一个对象里：
 * <ul>
 *   <li>{@code amountLabel} —— 档位区间的用量口径（提示文案用）；</li>
 *   <li>{@code basePriceFields} —— 该模式表单上的基础价字段到档位单价字段的映射，
 *       null 表示该模式没有分项基础价可供档位铺垫（档位单价需逐项手填）。</li>
 * </ul>
 * 后续再有模式开放阶梯时，只需在此追加一条，UI 层无需改动。
 */
export interface TierScope {
  /** 档位命中的用量口径文案 */
  amountLabel: string;
  /** 单价字段 -> 该模式表单上的基础价字段名；null 表示无分项基础价 */
  basePriceFields: Record<keyof TierPriceSet, string> | null;
}

const TOKEN_BASE_PRICE_FIELDS: Record<keyof TierPriceSet, string> = {
  input: "inputUnitPrice",
  output: "outputUnitPrice",
  cacheRead: "cacheReadUnitPrice",
  cacheWrite: "cacheWriteUnitPrice",
  cacheWrite5m: "cacheWrite5mUnitPrice",
  cacheWrite1h: "cacheWrite1hUnitPrice"
};

/** 按计费模式取阶梯口径；未开放阶梯的模式回退通用口径（当前实际只有 TOKEN 走到分支一） */
export function tierScope(mode: unknown): TierScope {
  return String(mode || "").trim().toUpperCase() === "TOKEN"
    ? { amountLabel: "本次请求总 Token", basePriceFields: TOKEN_BASE_PRICE_FIELDS }
    : { amountLabel: "本次请求用量", basePriceFields: null };
}

/**
 * 取当前模式可作为档位单价来源的基础价；该模式没有分项基础价时返回 null。
 * 供「新增档位预填」「一键填入基础价」使用。
 */
export function basePricesOf(form: Dict | null, mode: unknown): TierPriceSet | null {
  const fields = tierScope(mode).basePriceFields;
  if (!fields) {
    return null;
  }
  const prices = createTierPriceSet(null);
  for (const field of TIER_PRICE_FIELDS) {
    prices[field.key] = toNumberOrNull(form?.[fields[field.key]]);
  }
  return prices;
}

/** 取指定档位指定字段的错误文案（用于表单项内联红字） */
export function issueMessage(issues: TierIssue[], index: number, field: TierIssue["field"]): string {
  const matched = issues.find((issue) => issue.index === index && issue.field === field);
  return matched ? matched.message : "";
}

/** 档位行的稳定 key：v-for 删除/排序后输入框不串位 */
let uidSeed = 0;

function nextUid(): string {
  uidSeed += 1;
  return `tier-${uidSeed}`;
}

/** 把后端可能的多形态输入（裸数组 / JSON 文本 / null）统一成一个数组 */
function normalizeArray(raw: unknown): unknown[] {
  if (raw == null || raw === "") {
    return [];
  }
  let candidate = raw;
  if (typeof candidate === "string") {
    try {
      candidate = JSON.parse(candidate);
    } catch {
      return [];
    }
  }
  return Array.isArray(candidate) ? candidate : [];
}
