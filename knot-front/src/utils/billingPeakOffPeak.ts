import type {Dict} from "@/types";

/**
 * 高低峰定价（pricingPlan=PEAK_OFF_PEAK）的领域模型：解析、序列化、校验、本地判定。
 *
 * <p>与后端 {@code org.chobit.knot.gateway.model.BillingConfig.Pricing} 与
 * {@code PeakOffPeakResolver} 严格对齐：
 * <ul>
 *   <li>方案类型不写进 JSON（由版本列 {@code pricing_plan} 表达），因此这里也没有 {@code type} 根节点；</li>
 *   <li>{@code rateMode} 首期固定 {@code MULTIPLIER}，{@code timezone} 白名单只有 {@code UTC}；</li>
 *   <li>{@code phases} 首项必须是 PEAK，末项必须是 {@code condition.type=DEFAULT} 的兜底低峰；</li>
 *   <li>倍率落在 {@code (0, 1]}：低峰是打折，不允许免费也不允许涨价；</li>
 *   <li>时段 {@code HH:mm} 左闭右开，禁止跨午夜，同一条件内不得重叠；
 *       {@code end} 允许特例 {@code 24:00}（= 次日 0 点）以表达「覆盖到午夜」；</li>
 *   <li>全部时段先换算到「当天 0 点起算分钟数轴」再比较（{@code 24:00} 即 1440），
 *       校验 / 判定 / 时间轴共用 {@link toMinuteRange} 一个出口，不散落字符串特判；</li>
 *   <li>多条高峰规则的调休策略必须一致（后端判定只读第一条，不一致会被拒绝保存）。</li>
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

/** 一条相位规则：命中条件 -> 相位 + 倍率 */
export interface PeakPhaseRow {
  /** 仅用于前端 v-for 稳定 key，不参与序列化 */
  uid?: string;
  phase: PeakPhaseCode;
  multiplier: number | null;
  /** 兜底低峰（末项）：不参与星期/时段判定 */
  isDefault: boolean;
  /** ISO DayOfWeek 名（Java 枚举名），如 ["MONDAY","FRIDAY"] */
  weekdays: string[];
  windows: PeakWindow[];
  /** 调休日策略：OFF_PEAK（按低峰）/ FOLLOW_WEEKDAY_WINDOWS（按工作日窗口判定） */
  makeUpWorkdayPolicy: string;
}

export interface PeakPricing {
  timezone: string;
  phases: PeakPhaseRow[];
}

/** 校验结果：定位到相位序号与具体字段 */
export interface PeakIssue {
  index: number;
  field?: string;
  message: string;
}

/** 一周七天：ISO DayOfWeek 枚举名 -> 中文（顺序即展示顺序） */
export const PEAK_WEEKDAYS: ReadonlyArray<{ code: string; label: string; short: string }> = [
  { code: "MONDAY", label: "周一", short: "一" },
  { code: "TUESDAY", label: "周二", short: "二" },
  { code: "WEDNESDAY", label: "周三", short: "三" },
  { code: "THURSDAY", label: "周四", short: "四" },
  { code: "FRIDAY", label: "周五", short: "五" },
  { code: "SATURDAY", label: "周六", short: "六" },
  { code: "SUNDAY", label: "周日", short: "日" }
];

/** 首期时区白名单：只接受 UTC，禁止 +08:00 这类固定偏移（与后端 SUPPORTED_TIMEZONES 同源） */
export const PEAK_TIMEZONE_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
  { value: "UTC", label: "UTC" }
];

/** 调休日策略；节假日策略首期固定 OFF_PEAK，无选项 */
export const PEAK_MAKE_UP_POLICIES: ReadonlyArray<{ value: string; label: string; hint: string }> = [
  { value: "OFF_PEAK", label: "整日低峰", hint: "调休上班日一律按低峰倍率计费" },
  { value: "FOLLOW_WEEKDAY_WINDOWS", label: "按工作日窗口", hint: "调休日忽略当天星期，按所调休星期的高峰窗口判定" }
];

const DEFAULT_MAKE_UP_POLICY = "OFF_PEAK";
const HOLIDAY_POLICY = "OFF_PEAK";

/** 判定原因；与后端 PhaseReason 同名 */
export const PEAK_REASONS: Record<string, string> = {
  PEAK_WINDOW: "命中高峰时段",
  HOLIDAY: "节假日整日低峰",
  MAKE_UP_WORKDAY: "调休上班日按策略判低峰",
  DEFAULT: "未命中任何高峰时段，走兜底低峰",
  NO_TIMESTAMP: "缺少发生时间，不调整"
};

export const PEAK_PHASE_LABELS: Record<string, string> = {
  PEAK: "高峰",
  OFF_PEAK: "低峰"
};

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
    isDefault: init.isDefault ?? false,
    weekdays: init.weekdays ? [...init.weekdays] : [],
    windows: init.windows ? init.windows.map((item) => ({ ...item, uid: item.uid ?? nextUid("window") })) : [],
    makeUpWorkdayPolicy: init.makeUpWorkdayPolicy ?? DEFAULT_MAKE_UP_POLICY
  };
}

/** 新建规则时的默认骨架：工作日 01:00-04:00 为高峰，其余一律低峰 0.8 倍 */
export function createDefaultPeakPricing(): PeakPricing {
  return {
    timezone: "UTC",
    phases: [
      createPeakPhaseRow({
        phase: "PEAK",
        multiplier: 1,
        weekdays: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
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
      ? condition.weekdays.map((code) => String(code || "").trim().toUpperCase()).filter(Boolean)
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
      isDefault: String(condition.type || "").trim().toUpperCase() === "DEFAULT",
      weekdays,
      windows,
      makeUpWorkdayPolicy: String(condition.makeUpWorkdayPolicy || DEFAULT_MAKE_UP_POLICY).trim().toUpperCase()
    });
  });
  return {
    timezone: String(source.timezone || "UTC").trim() || "UTC",
    phases
  };
}

/** 表单结构 -> 后端 config.pricing；兜底项的 condition 只带 type=DEFAULT */
export function toPeakPayload(pricing: PeakPricing | null): Dict | null {
  if (!pricing) {
    return null;
  }
  const phases = (pricing.phases || []).map((row) => {
    if (row.isDefault) {
      return {
        condition: { type: "DEFAULT" },
        phase: row.phase,
        multiplier: toNumberOrNull(row.multiplier) ?? 1
      };
    }
    return {
      condition: {
        weekdays: row.weekdays,
        windows: row.windows.map((window) => ({ start: window.start, end: window.end })),
        holidayPolicy: HOLIDAY_POLICY,
        makeUpWorkdayPolicy: row.makeUpWorkdayPolicy || DEFAULT_MAKE_UP_POLICY
      },
      phase: row.phase,
      multiplier: toNumberOrNull(row.multiplier) ?? 1
    };
  });
  return {
    rateMode: "MULTIPLIER",
    timezone: pricing.timezone || "UTC",
    phases
  };
}

/** 校验高低峰配置，返回可读错误列表；空数组表示合法（镜像后端 validatePricing） */
export function validatePeakPricing(pricing: PeakPricing | null): PeakIssue[] {
  if (!pricing) {
    return [{ index: -1, message: "高低峰方案缺少 pricing 配置" }];
  }
  if (!PEAK_TIMEZONE_OPTIONS.some((item) => item.value === pricing.timezone)) {
    return [{ index: -1, message: `时区只支持 ${PEAK_TIMEZONE_OPTIONS.map((item) => item.value).join("、")}` }];
  }
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

  /** 判定器只读第一条高峰规则的调休策略，各条不一致会导致静默忽略 -> 后端拒绝保存，前端先拦 */
  let makeUpPolicy: string | null = null;

  phases.forEach((row, index) => {
    const seq = index + 1;
    const multiplier = toNumberOrNull(row.multiplier);
    if (multiplier == null) {
      issues.push({ index, field: "multiplier", message: `第 ${seq} 条：倍率必填` });
    } else if (multiplier <= 0) {
      issues.push({ index, field: "multiplier", message: `第 ${seq} 条：倍率必须大于 0` });
    } else if (multiplier > 1) {
      issues.push({ index, field: "multiplier", message: `第 ${seq} 条：倍率不能大于 1（低峰是打折，不允许涨价）` });
    }
    if (row.isDefault) {
      return;
    }
    const currentPolicy = row.makeUpWorkdayPolicy || DEFAULT_MAKE_UP_POLICY;
    if (makeUpPolicy == null) {
      makeUpPolicy = currentPolicy;
    } else if (makeUpPolicy !== currentPolicy) {
      issues.push({
        index,
        field: "makeUpWorkdayPolicy",
        message: `第 ${seq} 条：调休策略必须与其它高峰规则一致（判定只认第一条）`
      });
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

/** 取指定相位指定字段的错误文案 */
export function peakIssueMessage(issues: PeakIssue[], index: number, field: string): string {
  const matched = issues.find((issue) => issue.index === index && issue.field === field);
  return matched ? matched.message : "";
}

/** 该相位是否在指定星期生效（用于时间轴高亮） */
export function coversWeekday(row: PeakPhaseRow, weekdayCode: string): boolean {
  return !row.isDefault && row.weekdays.includes(weekdayCode);
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
}

/**
 * 本地判定给定时刻的相位（镜像 {@code PeakOffPeakResolver.resolve}）。
 *
 * <p>与后端的差异只有一处：日历数据源。首期后端注入的是空日历，因此这里不判节假日/调休，
 * 结果只反映「星期 + 时段」；接入真实日历时两侧一起补。
 *
 * @param at ISO 日期 {@code yyyy-MM-dd} 与 UTC 时刻 {@code HH:mm}
 */
export function resolvePeakPhase(pricing: PeakPricing | null, date: string, time: string): PeakDecision | null {
  const minutes = parseClock(time);
  if (!pricing || !date || minutes == null) {
    return null;
  }
  const parsed = new Date(`${date}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }
  // JS getUTCDay: 0=周日；Java DayOfWeek: 周一=1 ... 周日=7
  const javaDay = ((parsed.getUTCDay() + 6) % 7) + 1;
  const weekdayCode = PEAK_WEEKDAYS[javaDay - 1]?.code;
  const phases = pricing.phases || [];
  const fallback = phases[phases.length - 1];
  const fallbackMultiplier = toNumberOrNull(fallback?.multiplier) ?? 1;
  for (const row of phases) {
    if (row.isDefault || row.phase !== "PEAK") {
      continue;
    }
    if (!weekdayCode || !row.weekdays.includes(weekdayCode)) {
      continue;
    }
    const hit = row.windows.some((window) => {
      const range = toMinuteRange(window);
      return range != null && minutes >= range.startMinute && minutes < range.endMinute;
    });
    if (hit) {
      return { phase: "PEAK", reason: "PEAK_WINDOW", multiplier: toNumberOrNull(row.multiplier) ?? 1 };
    }
  }
  return { phase: "OFF_PEAK", reason: "DEFAULT", multiplier: fallbackMultiplier };
}
