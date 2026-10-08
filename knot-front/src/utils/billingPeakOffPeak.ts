import type {Dict} from "@/types";

/**
 * 高低峰定价（pricingPlan=PEAK_OFF_PEAK）的领域模型：解析、序列化、校验、本地判定。
 *
 * <p>与后端 {@code org.chobit.knot.gateway.model.BillingConfig.Pricing} 与
 * {@code PeakOffPeakResolver} 严格对齐：
 * <ul>
 *   <li>方案类型不写进 JSON（由版本列 {@code pricing_plan} 表达），因此这里也没有 {@code type} 根节点；</li>
 *   <li>{@code rateMode} 首期固定 {@code MULTIPLIER}，{@code timezone} 使用 UTC 偏移；</li>
 *   <li>{@code phases} 首项必须是 PEAK，末项必须是 {@code condition.type=DEFAULT} 的兜底低峰；</li>
 *   <li>倍率落在 {@code (0, 1]}：低峰是打折，不允许免费也不允许涨价；</li>
 *   <li>时段 {@code HH:mm} 左闭右开，禁止跨午夜，同一条件内不得重叠；
 *       {@code end} 允许特例 {@code 24:00}（= 次日 0 点）以表达「覆盖到午夜」；</li>
 *   <li>全部时段先换算到「当天 0 点起算分钟数轴」再比较（{@code 24:00} 即 1440），
 *       校验 / 判定 / 时间轴共用 {@link toMinuteRange} 一个出口，不散落字符串特判；</li>
 * </ul>
 *
 * <p>方案层**不持有价格**：它只输出相位与倍率，价格本体来自模式层（TOKEN 的 basePrices、
 * 其余模式的 defaultUnitPrice）。因此本模块不出现任何模式专属字段。
 *
 * <p>⚠ 改任一侧都要两边同步：校验规则镜像 {@code BillingConfig.validatePricing}，
 * 判定规则镜像 {@code PeakOffPeakResolver.resolve}。
 */

/** 相位；后端 PricingPhase 只有这两个取值 */
export type PeakPhaseCode = "PEAK" | "OFF_PEAK";

/** 价随相位变化的模式：MULTIPLIER=基础价×倍率；ABSOLUTE=命中位相自身单价（非倍数） */
export type PeakRateMode = "MULTIPLIER" | "ABSOLUTE";

/** 独立单价（ABSOLUTE 模式）：每个相位自带的分项价格；缺省字段视为未配置 */
export interface PeakUnitPrice {
  input?: number | null;
  output?: number | null;
  cacheRead?: number | null;
  cacheWrite?: number | null;
  cacheWrite5m?: number | null;
  cacheWrite1h?: number | null;
}

/** end 的午夜特例：表示次日 0 点（左闭右开的右端点），镜像后端 BillingConfig.CLOCK_MIDNIGHT_END */
export const PEAK_MIDNIGHT_END = "24:00";

/** 一天的分钟数（数轴端点：24:00 = 1440），镜像后端 BillingConfig.MINUTES_OF_DAY */
export const PEAK_MINUTES_OF_DAY = 1440;

/** 日内时段：start 含、end 不含（左闭右开）；start < end，禁止跨午夜 */
export interface PeakWindow {
  /** 仅用于前端 v-for 稳定 key，不参与序列化 */
  uid?: string;
  start: string;
  /** 允许特例 "24:00"（覆盖到午夜）；其余为 HH:mm */
  end: string;
}

/** 一条相位规则：命中条件 -> 相位 + 倍率（或独立单价） */
export interface PeakPhaseRow {
  /** 仅用于前端 v-for 稳定 key，不参与序列化 */
  uid?: string;
  phase: PeakPhaseCode;
  multiplier: number | null;
  /** ABSOLUTE 模式：该相位自身携带的分项单价 */
  unitPrices?: PeakUnitPrice | null;
  /** 兜底低峰（末项）：不参与星期/时段判定 */
  isDefault: boolean;
  /** ISO DayOfWeek 数字（周一=1 ... 周日=7），与后端 PhaseCondition.weekdays 同口径 */
  weekdays: number[];
  windows: PeakWindow[];
  /**
   * 调休策略（OFF_PEAK / FOLLOW_WEEKDAY_WINDOWS）；后端可选字段。
   * 前端只做透传保真（config_json 页展示不丢字段），本地判定不消费它。
   */
  makeUpWorkdayPolicy?: string;
}

export interface PeakPricing {
  /** 价随相位变化的模式；缺省按 MULTIPLIER 处理 */
  rateMode?: PeakRateMode;
  timezone: string;
  phases: PeakPhaseRow[];
}

/** 校验结果：定位到相位序号与具体字段 */
export interface PeakIssue {
  index: number;
  field?: string;
  message: string;
}

/** 一周七天：ISO DayOfWeek 数字（周一=1 ... 周日=7）-> 中文（顺序即展示顺序） */
export const PEAK_WEEKDAYS: ReadonlyArray<{ value: number; label: string; short: string }> = [
  { value: 1, label: "周一", short: "一" },
  { value: 2, label: "周二", short: "二" },
  { value: 3, label: "周三", short: "三" },
  { value: 4, label: "周四", short: "四" },
  { value: 5, label: "周五", short: "五" },
  { value: 6, label: "周六", short: "六" },
  { value: 7, label: "周日", short: "日" }
];

/** 存量数据兼容解析：旧版 weekdays 存的是 ISO 枚举名（"MONDAY"），编辑保存时统一转数字 */
const LEGACY_WEEKDAY_CODES: Record<string, number> = {
  MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4, FRIDAY: 5, SATURDAY: 6, SUNDAY: 7
};

/** UTC 偏移列表；与后端 BillingConfig 的白名单保持一致。 */
export const PEAK_TIMEZONE_OPTIONS: ReadonlyArray<{ value: string; label: string }> = Array.from(
  {length: 27},
  (_, index) => {
    const offset = index - 12;
    const value = offset <= 0 ? `UTC${offset < 0 ? offset : "+0"}` : `UTC+${offset}`;
    return {value, label: value};
  }
);

const HOLIDAY_POLICY = "OFF_PEAK";

/** 判定原因；与后端 PhaseReason 同名 */
export const PEAK_REASONS: Record<string, string> = {
  PEAK_WINDOW: "命中高峰时段",
  HOLIDAY: "节假日整日低峰",
  DEFAULT: "未命中任何高峰时段，走兜底低峰",
  NO_TIMESTAMP: "缺少发生时间，不调整"
};

export const PEAK_PHASE_LABELS: Record<string, string> = {
  PEAK: "高峰",
  OFF_PEAK: "低峰"
};

/** 相位自带单价的字段清单（ABSOLUTE 模式）：顺序即展示顺序 */
export const PEAK_UNIT_PRICE_FIELDS: ReadonlyArray<{ key: keyof PeakUnitPrice; label: string }> = [
  { key: "input", label: "输入单价" },
  { key: "output", label: "输出单价" },
  { key: "cacheRead", label: "缓存读单价" },
  { key: "cacheWrite", label: "缓存写单价" },
  { key: "cacheWrite5m", label: "缓存写(5m)单价" },
  { key: "cacheWrite1h", label: "缓存写(1h)单价" }
];

/** 宽松转数字：空值与非法值统一归一为 null */
export function toNumberOrNull(value: unknown): number | null {
  if (value == null || value === "") {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

let peakUidSeed = 0;

function nextUid(prefix: string): string {
  peakUidSeed += 1;
  return `${prefix}-${peakUidSeed}`;
}

/**
 * {@code HH:mm} -> 当日分钟数；非法格式返回 null。
 *
 * <p>**不接受 24:00**——它不是一个时刻（当日没有 24 点），只是区间右端点。
 * 需要处理区间端点请用 {@link parseClockEndpoint}。
 *
 * <p>格式严格到两位小时：{@code 1:00} 这类放宽写法会被拒（镜像后端手工解析），
 * 否则前端认为合法、后端 400，用户看到的是莫名其妙的服务端错误。
 */
export function parseClock(value: unknown): number | null {
  const text = String(value ?? "").trim();
  const matched = /^(\d{2}):(\d{2})$/.exec(text);
  if (!matched) {
    return null;
  }
  const hours = Number(matched[1]);
  const minutes = Number(matched[2]);
  if (hours > 23 || minutes > 59) {
    return null;
  }
  return hours * 60 + minutes;
}

/**
 * 区间端点 -> 分钟数：在 {@link parseClock} 基础上额外接受 {@code 24:00}（= {@link PEAK_MINUTES_OF_DAY}）。
 *
 * <p>镜像后端 {@code BillingConfig.windowRangeOf}，供 end 解析与整段区间换算使用。
 */
export function parseClockEndpoint(value: unknown): number | null {
  const text = String(value ?? "").trim();
  if (text === PEAK_MIDNIGHT_END) {
    return PEAK_MINUTES_OF_DAY;
  }
  return parseClock(text);
}

/** 分钟区间 {@code [startMinute, endMinute)}，与后端 WindowRange 同名同义 */
export interface PeakMinuteRange {
  startMinute: number;
  endMinute: number;
}

/**
 * 时段 -> 分钟数轴区间的**唯一换算出口**（镜像后端 {@code BillingConfig.windowRangeOf}）。
 *
 * <p>robin 的口径：窗口一律先换算成「当天 0 点起算的分钟数」区间再比较，
 * {@code 24:00} 就是数轴端点 1440（= 明天 0 点），因此「今天的区间」可写成 {@code [0, 1440)}。
 * 校验、判定（{@link resolvePeakPhase}）、时间轴（{@link coversHour}）全部走这里，不各自解析。
 * 任一端格式非法返回 null。
 */
export function toMinuteRange(window: PeakWindow | null | undefined): PeakMinuteRange | null {
  if (!window) {
    return null;
  }
  const startMinute = parseClock(window.start);
  if (startMinute == null) {
    return null;
  }
  const endMinute = parseClockEndpoint(window.end);
  return endMinute == null ? null : { startMinute, endMinute };
}

/** 分钟数 -> HH:mm；1440 输出午夜特例 24:00 */
export function formatClock(minutes: number | null): string {
  if (minutes == null) {
    return "";
  }
  if (minutes >= PEAK_MINUTES_OF_DAY) {
    return PEAK_MIDNIGHT_END;
  }
  const hours = Math.floor(minutes / 60);
  return `${String(hours).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
}

export function createWindow(start = "09:00", end = "18:00"): PeakWindow {
  return { uid: nextUid("window"), start, end };
}

/** 新时段的默认值取「上一段 end」起，避免新增即重叠 */
export function nextWindowStart(previousEnd: string | null | undefined): string {
  const endMinute = parseClockEndpoint(previousEnd);
  if (endMinute == null || endMinute >= PEAK_MINUTES_OF_DAY) {
    return "09:00";
  }
  return formatClock(endMinute);
}

export function createPeakPhaseRow(init: Partial<PeakPhaseRow> = {}): PeakPhaseRow {
  return {
    uid: init.uid ?? nextUid("phase"),
    phase: init.phase ?? "PEAK",
    multiplier: init.multiplier ?? null,
    unitPrices: init.unitPrices ? { ...init.unitPrices } : null,
    isDefault: init.isDefault ?? false,
    weekdays: init.weekdays ? [...init.weekdays] : [],
    windows: init.windows ? init.windows.map((item) => ({ ...item, uid: item.uid ?? nextUid("window") })) : [],
    makeUpWorkdayPolicy: init.makeUpWorkdayPolicy,
  };
}

/** 新建规则时的默认骨架：工作日 01:00-04:00 为高峰，其余一律低峰 0.8 倍 */
export function createDefaultPeakPricing(): PeakPricing {
  return {
    timezone: "UTC+0",
    phases: [
      createPeakPhaseRow({
        phase: "PEAK",
        multiplier: 1,
        weekdays: [1, 2, 3, 4, 5],
        windows: [createWindow("01:00", "04:00")]
      }),
      createPeakPhaseRow({ phase: "OFF_PEAK", multiplier: 0.8, isDefault: true })
    ]
  };
}

/** 后端 config.pricing -> 表单结构；结构缺失或形态不对时返回 null 由调用方兜底 */
export function parsePeakPricing(raw: unknown): PeakPricing | null {
  const source = raw && typeof raw === "object" ? (raw as Dict) : null;
  if (!source) {
    return null;
  }
  const list = Array.isArray(source.phases) ? source.phases : null;
  if (!list || !list.length) {
    return null;
  }
  const phases = list.map((item) => {
    const phase = (item && typeof item === "object" ? item : {}) as Dict;
    const condition = (phase.condition && typeof phase.condition === "object" ? phase.condition : {}) as Dict;
    const weekdays = Array.isArray(condition.weekdays)
      ? condition.weekdays
          .map((item) => {
            if (typeof item === "number" && Number.isInteger(item) && item >= 1 && item <= 7) {
              return item;
            }
            // 存量数据是枚举名（"MONDAY"），读取时转数字；序列化只输出数字
            return LEGACY_WEEKDAY_CODES[String(item || "").trim().toUpperCase()] ?? null;
          })
          .filter((value): value is number => value != null)
      : [];
    const windows = Array.isArray(condition.windows)
      ? condition.windows.map((item2) => {
          const window = (item2 && typeof item2 === "object" ? item2 : {}) as Dict;
          // 原样保留 start/end（含 24:00 特例），不经默认值覆盖
          return { uid: nextUid("window"), start: String(window.start ?? ""), end: String(window.end ?? "") };
        })
      : [];
    return createPeakPhaseRow({
      phase: String(phase.phase || "").trim().toUpperCase() === "OFF_PEAK" ? "OFF_PEAK" : "PEAK",
      multiplier: toNumberOrNull(phase.multiplier),
      unitPrices: parsePeakUnitPrices(phase.unitPrices),
      isDefault: String(condition.type || "").trim().toUpperCase() === "DEFAULT",
      weekdays,
      windows,
      makeUpWorkdayPolicy: String(condition.makeUpWorkdayPolicy ?? "").trim() || undefined,
    });
  });
  return {
    rateMode: pricingRateModeOf(source.rateMode),
    timezone: normalizeTimezone(String(source.timezone || "UTC+0").trim() || "UTC+0"),
    phases
  };
}

/** 读取相位自带单价（ABSOLUTE 模式）：只保留数值字段，非数字统一归一为 null */
function parsePeakUnitPrices(raw: unknown): PeakUnitPrice | null {
  const source = raw && typeof raw === "object" ? (raw as Dict) : null;
  if (!source) {
    return null;
  }
  const keys: (keyof PeakUnitPrice)[] = [
    "input", "output", "cacheRead", "cacheWrite", "cacheWrite5m", "cacheWrite1h"
  ];
  const result: PeakUnitPrice = {};
  for (const key of keys) {
    const value = toNumberOrNull(source[key as string]);
    if (value != null) {
      result[key] = value;
    }
  }
  // 一项都没配置视为空（等价于未设置）
  return Object.keys(result).length ? result : null;
}

/** 归一 rateMode：缺省或非法按 MULTIPLIER 处理，避免脏数据绕过校验 */
function pricingRateModeOf(value: unknown): PeakRateMode {
  return String(value || "").trim().toUpperCase() === "ABSOLUTE" ? "ABSOLUTE" : "MULTIPLIER";
}

/** 表单结构 -> 后端 config.pricing；兜底项的 condition 只带 type=DEFAULT */
export function toPeakPayload(pricing: PeakPricing | null): Dict | null {
  if (!pricing) {
    return null;
  }
  const rateMode = pricingRateModeOf(pricing.rateMode);
  const phases = (pricing.phases || []).map((row) => {
    const condition = row.isDefault
      ? { type: "DEFAULT" }
      : {
          weekdays: row.weekdays,
          windows: row.windows.map((window) => ({ start: window.start, end: window.end })),
          holidayPolicy: HOLIDAY_POLICY,
          // 存量配置带调休策略时透传，避免往返重建丢字段；前端新建的骨架没有该字段则不输出
          ...(row.makeUpWorkdayPolicy ? { makeUpWorkdayPolicy: row.makeUpWorkdayPolicy } : {})
        };
    if (rateMode === "ABSOLUTE") {
      // ABSOLUTE：只输出命中位相自身单价，不做倍数
      return {
        condition,
        phase: row.phase,
        unitPrices: toUnitPricesPayload(row.unitPrices)
      };
    }
    return {
      condition,
      phase: row.phase,
      multiplier: toNumberOrNull(row.multiplier) ?? 1
    };
  });
  return {
    rateMode,
    timezone: normalizeTimezone(pricing.timezone || "UTC+0"),
    phases
  };
}

/** ABSOLUTE 相位单价 -> 后端 unitPrices：只输出已填写的数值字段 */
function toUnitPricesPayload(unitPrices: PeakUnitPrice | null | undefined): Dict {
  const result: Dict = {};
  if (unitPrices) {
    const keys: (keyof PeakUnitPrice)[] = [
      "input", "output", "cacheRead", "cacheWrite", "cacheWrite5m", "cacheWrite1h"
    ];
    for (const key of keys) {
      const value = toNumberOrNull(unitPrices[key]);
      if (value != null) {
        result[key as string] = value;
      }
    }
  }
  return result;
}

/** 单价字段标签（ABSOLUTE 校验/提示用） */
function peakUnitPriceLabel(key: keyof PeakUnitPrice): string {
  return PEAK_UNIT_PRICE_FIELDS.find((item) => item.key === key)?.label ?? String(key);
}

/**
 * 校验高低峰配置，返回可读错误列表；空数组表示合法（镜像后端 validatePricing）。
 *
 * <p>{@code billingMode} 仅在 ABSOLUTE 模式下用于决定单价的「必填字段集」：
 * TOKEN 要求 6 项分项单价齐全，简单模式（REQUEST/IMAGE/AUDIO/VIDEO/EMBEDDING）只要求 input。
 * MULTIPLIER 模式忽略该参数，仍只校验倍率。
 */
export function validatePeakPricing(pricing: PeakPricing | null, billingMode?: string, cacheWriteMode?: string): PeakIssue[] {
  if (!pricing) {
    return [{ index: -1, message: "高低峰方案缺少 pricing 配置" }];
  }
  if (!PEAK_TIMEZONE_OPTIONS.some((item) => item.value === pricing.timezone)) {
    return [{ index: -1, message: "时区必须选择 UTC-12 至 UTC+14 的偏移" }];
  }
  const rateMode = pricingRateModeOf(pricing.rateMode);
  const isAbsolute = rateMode === "ABSOLUTE";
  const isTokenMode = billingMode === "TOKEN";
  const phases = pricing.phases || [];
  if (!phases.length) {
    return [{ index: -1, message: "至少需要配置 1 条相位规则" }];
  }
  const issues: PeakIssue[] = [];
  if (phases[0].isDefault || phases[0].phase !== "PEAK") {
    issues.push({ index: 0, message: "首条相位规则必须是高峰（PEAK）" });
  }
  const last = phases[phases.length - 1];
  if (!last.isDefault || last.phase !== "OFF_PEAK") {
    issues.push({ index: phases.length - 1, message: "末条必须是兜底低峰（condition.type=DEFAULT，phase=OFF_PEAK）" });
  }

  phases.forEach((row, index) => {
    const seq = index + 1;
    if (isAbsolute) {
      // ABSOLUTE：每个相位必须自带单价（必填 + 非负）；TOKEN 要求 6 项齐全，简单模式只要求 input
      const unitIssue = validateAbsolutePhaseUnitPrices(row.unitPrices, index, seq, isTokenMode, cacheWriteMode);
      if (unitIssue) {
        issues.push(unitIssue);
      }
    } else {
      const multiplier = toNumberOrNull(row.multiplier);
      if (multiplier == null) {
        issues.push({ index, field: "multiplier", message: `第 ${seq} 条：倍率必填` });
      } else if (multiplier <= 0) {
        issues.push({ index, field: "multiplier", message: `第 ${seq} 条：倍率必须大于 0` });
      } else if (multiplier > 1) {
        issues.push({ index, field: "multiplier", message: `第 ${seq} 条：倍率不能大于 1（低峰是打折，不允许涨价）` });
      }
    }
    if (row.isDefault) {
      return;
    }
    if (!row.weekdays.length) {
      issues.push({ index, field: "weekdays", message: `第 ${seq} 条：适用星期至少选 1 天` });
    }
    if (!row.windows.length) {
      issues.push({ index, field: "windows", message: `第 ${seq} 条：至少配置 1 个时段` });
      return;
    }
    // 重叠判定在分钟数轴上做：end=24:00 即 1440，与前一段的 end 直接可比
    let previousEndMinute = -1;
    row.windows.forEach((window, windowIndex) => {
      const seq2 = `第 ${seq} 条第 ${windowIndex + 1} 段`;
      const range = toMinuteRange(window);
      if (!range) {
        issues.push({ index, field: `windows.${windowIndex}`, message: `${seq2}：时段需使用 HH:mm 格式（结束可为 24:00）` });
        return;
      }
      if (range.startMinute >= range.endMinute) {
        issues.push({
          index,
          field: `windows.${windowIndex}`,
          message: `${seq2}：开始须早于结束，且不允许跨午夜（请拆成两段）`
        });
        return;
      }
      if (previousEndMinute >= 0 && range.startMinute < previousEndMinute) {
        issues.push({ index, field: `windows.${windowIndex}`, message: `${seq2}：与上一个时段重叠` });
      }
      previousEndMinute = range.endMinute;
    });
  });
  return issues;
}

/** ABSOLUTE 模式单相位单价校验：必填字段缺失或为负报错（TOKEN 要求 6 项，简单模式只要求 input） */
function validateAbsolutePhaseUnitPrices(
  unitPrices: PeakUnitPrice | null | undefined,
  index: number,
  seq: number,
  isTokenMode: boolean,
  cacheWriteMode?: string
): PeakIssue | null {
  const prices = unitPrices && typeof unitPrices === "object" ? unitPrices : null;
  // TOKEN 模式必填字段集随「缓存写方式」联动：standard 填 cacheWrite，ttl 填 cacheWrite5m/1h，
  // 与 BillingRuleFormDialog 的 cacheWriteMode 选择及 toPeakPayload 输出口径一致。
  const required: (keyof PeakUnitPrice)[] = isTokenMode
    ? (cacheWriteMode === "ttl"
        ? ["input", "output", "cacheRead", "cacheWrite5m", "cacheWrite1h"]
        : ["input", "output", "cacheRead", "cacheWrite"])
    : ["input"];
  for (const key of required) {
    const value = toNumberOrNull(prices?.[key]);
    if (value == null) {
      return { index, field: `unitPrices.${key}`, message: `第 ${seq} 条（ABSOLUTE）：${peakUnitPriceLabel(key)}必填` };
    }
    if (value < 0) {
      return { index, field: `unitPrices.${key}`, message: `第 ${seq} 条（ABSOLUTE）：${peakUnitPriceLabel(key)}不能为负数` };
    }
  }
  // 简单模式允许填写其余分项，但填了就必须非负
  if (!isTokenMode && prices) {
    const optional: (keyof PeakUnitPrice)[] = ["output", "cacheRead", "cacheWrite", "cacheWrite5m", "cacheWrite1h"];
    for (const key of optional) {
      const value = toNumberOrNull(prices[key]);
      if (value != null && value < 0) {
        return { index, field: `unitPrices.${key}`, message: `第 ${seq} 条（ABSOLUTE）：${peakUnitPriceLabel(key)}不能为负数` };
      }
    }
  }
  return null;
}

/** 取指定相位指定字段的错误文案 */
export function peakIssueMessage(issues: PeakIssue[], index: number, field: string): string {
  const matched = issues.find((issue) => issue.index === index && issue.field === field);
  return matched ? matched.message : "";
}

/** 该相位是否在指定星期生效（用于时间轴高亮） */
export function coversWeekday(row: PeakPhaseRow, day: number): boolean {
  return !row.isDefault && row.weekdays.includes(day);
}

/** 某一小时是否落在相位窗口内（时间轴渲染用：小时起点落在窗口内即算命中） */
export function coversHour(row: PeakPhaseRow, hour: number): boolean {
  if (row.isDefault) {
    return false;
  }
  const minutes = hour * 60;
  return row.windows.some((window) => {
    const range = toMinuteRange(window);
    return range != null && minutes >= range.startMinute && minutes < range.endMinute;
  });
}

/** 时段展示文案：24:00 读作「午夜」，便于列表/抽屉里一眼看懂 */
export function describePeakWindow(window: PeakWindow): string {
  const end = String(window.end ?? "").trim() === PEAK_MIDNIGHT_END ? "午夜" : window.end;
  return `${window.start}-${end}`;
}

/** 时间轴单元格提示文案 */
export function describePeakWindows(row: PeakPhaseRow): string {
  if (row.isDefault) {
    return "兜底低峰";
  }
  if (!row.windows.length) {
    return "未配置时段";
  }
  return row.windows.map(describePeakWindow).join("、");
}

export interface PeakDecision {
  phase: PeakPhaseCode;
  reason: string;
  multiplier: number;
  /** ABSOLUTE 模式：命中位相自身携带的分项单价；MULTIPLIER 模式恒为 null */
  unitPrices?: PeakUnitPrice | null;
}

/**
 * 本地判定给定时刻的相位（镜像 {@code PeakOffPeakResolver.resolve}）。
 *
 * <p>与后端的差异只有一处：日历数据源。首期后端注入的是空日历，因此这里不判节假日/调休，
 * 结果只反映「星期 + 时段」；接入真实日历时两侧一起补。
 *
 * @param at ISO 日期 {@code yyyy-MM-dd} 与所选 UTC 偏移下的本地时刻 {@code HH:mm}
 */
export function resolvePeakPhase(pricing: PeakPricing | null, date: string, time: string): PeakDecision | null {
  const minutes = parseClock(time);
  if (!pricing || !date || minutes == null) {
    return null;
  }
  const parsed = localDateTimeToInstant(date, time, pricing.timezone);
  if (!parsed) {
    return null;
  }
  const localDate = new Date(`${date}T00:00:00Z`);
  if (Number.isNaN(localDate.getTime())) {
    return null;
  }
  // JS getUTCDay: 0=周日；ISO DayOfWeek: 周一=1 ... 周日=7
  const javaDay = ((localDate.getUTCDay() + 6) % 7) + 1;
  const phases = pricing.phases || [];
  const fallback = phases[phases.length - 1];
  const isAbsolute = pricingRateModeOf(pricing.rateMode) === "ABSOLUTE";
  const fallbackUnitPrices = isAbsolute ? (fallback?.unitPrices ?? null) : null;
  for (const row of phases) {
    if (row.isDefault || row.phase !== "PEAK") {
      continue;
    }
    if (!row.weekdays.includes(javaDay)) {
      continue;
    }
    const hit = row.windows.some((window) => {
      const range = toMinuteRange(window);
      return range != null && minutes >= range.startMinute && minutes < range.endMinute;
    });
    if (hit) {
      if (isAbsolute) {
        return { phase: "PEAK", reason: "PEAK_WINDOW", multiplier: 1, unitPrices: row.unitPrices ?? null };
      }
      return { phase: "PEAK", reason: "PEAK_WINDOW", multiplier: toNumberOrNull(row.multiplier) ?? 1 };
    }
  }
  if (isAbsolute) {
    return { phase: "OFF_PEAK", reason: "DEFAULT", multiplier: 1, unitPrices: fallbackUnitPrices };
  }
  const fallbackMultiplier = toNumberOrNull(fallback?.multiplier) ?? 1;
  return { phase: "OFF_PEAK", reason: "DEFAULT", multiplier: fallbackMultiplier };
}

/** 将所选 UTC 偏移中的本地日期时间转换为瞬时点，供页面试算使用。 */
function localDateTimeToInstant(date: string, time: string, timezone: string): Date | null {
  const match = /^UTC([+-])(\d{1,2})$/.exec(normalizeTimezone(timezone));
  if (!match) {
    return null;
  }
  const hours = Number(match[2]);
  if (!Number.isInteger(hours) || hours > 14 || (match[1] === "-" && hours > 12)) {
    return null;
  }
  const naive = new Date(`${date}T${time}:00Z`);
  if (Number.isNaN(naive.getTime())) {
    return null;
  }
  const offsetMinutes = (match[1] === "+" ? 1 : -1) * hours * 60;
  return new Date(naive.getTime() - offsetMinutes * 60_000);
}

function normalizeTimezone(value: string): string {
  return value === "UTC" || value === "UTC-0" ? "UTC+0" : value;
}
