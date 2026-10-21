import { describe, it, expect } from "vitest";
import {
  parseClock,
  parseClockEndpoint,
  formatClock,
  toMinuteRange,
  coversWeekday,
  coversHour,
  validatePeakPricing,
  resolvePeakPhase,
  PEAK_MIDNIGHT_END,
  type PeakWindow,
  type PeakPhaseRow,
  type PeakPricing
} from "@/utils/billingPeakOffPeak";

// 计费高低峰（PEAK_OFF_PEAK）的本地判定与校验与后端 PeakOffPeakResolver 镜像。
// 时间轴换算统一走 toMinuteRange（24:00 = 1440），这里锁住时间解析与相位判定，
// 防止「1:00 被放行进而前后端 400 分歧」「倍率大于 1（变相涨价）」这类边界错。

describe("parseClock / parseClockEndpoint 时间解析", () => {
  it("HH:mm -> 当日分钟数", () => {
    expect(parseClock("00:00")).toBe(0);
    expect(parseClock("09:00")).toBe(540);
    expect(parseClock("23:59")).toBe(1439);
  });

  it("严格两位小时：1:00 这类放宽写法被拒", () => {
    expect(parseClock("1:00")).toBeNull();
    expect(parseClock("9:00")).toBeNull();
  });

  it("非法格式返回 null", () => {
    expect(parseClock("24:00")).toBeNull(); // 24:00 不是时刻
    expect(parseClock("25:00")).toBeNull();
    expect(parseClock("12:60")).toBeNull();
    expect(parseClock("")).toBeNull();
  });

  it("parseClockEndpoint 额外接受 24:00 = 1440", () => {
    expect(parseClockEndpoint("24:00")).toBe(1440);
    expect(parseClockEndpoint("09:00")).toBe(540);
    expect(parseClockEndpoint("25:00")).toBeNull();
  });
});

describe("formatClock 分钟 -> HH:mm", () => {
  it("正常格式化并垫零", () => {
    expect(formatClock(0)).toBe("00:00");
    expect(formatClock(540)).toBe("09:00");
  });

  it("1440 输出午夜特例 24:00，null 输出空串", () => {
    expect(formatClock(1440)).toBe(PEAK_MIDNIGHT_END);
    expect(formatClock(null)).toBe("");
  });
});

describe("toMinuteRange 区间换算单一出口", () => {
  it("正常时段", () => {
    expect(toMinuteRange({ start: "09:00", end: "18:00" })).toEqual({ startMinute: 540, endMinute: 1080 });
  });

  it("end=24:00 即数轴端点 1440", () => {
    expect(toMinuteRange({ start: "09:00", end: "24:00" })).toEqual({ startMinute: 540, endMinute: 1440 });
  });

  it("任一端非法返回 null", () => {
    expect(toMinuteRange({ start: "1:00", end: "18:00" })).toBeNull();
    expect(toMinuteRange({ start: "09:00", end: "25:00" })).toBeNull();
    expect(toMinuteRange(null)).toBeNull();
  });
});

describe("coversWeekday / coversHour 时间轴命中", () => {
  const row: PeakPhaseRow = {
    uid: "p",
    phase: "PEAK",
    multiplier: 1,
    unitPrices: null,
    isDefault: false,
    weekdays: [1, 2, 3, 4, 5],
    windows: [{ uid: "w", start: "09:00", end: "18:00" }]
  };

  it("coversWeekday 命中星期且不命中兜底项", () => {
    expect(coversWeekday(row, 1)).toBe(true);
    expect(coversWeekday(row, 7)).toBe(false);
    expect(coversWeekday({ ...row, isDefault: true }, 1)).toBe(false);
  });

  it("coversHour 落在窗口内（左闭右开）", () => {
    expect(coversHour(row, 9)).toBe(true); // 09:00 起点
    expect(coversHour(row, 17)).toBe(true);
    expect(coversHour(row, 18)).toBe(false); // 18:00 右端开
    expect(coversHour({ ...row, isDefault: true }, 9)).toBe(false);
  });
});

function peakPricing(): PeakPricing {
  return {
    timezone: "UTC+0",
    phases: [
      { uid: "a", phase: "PEAK", multiplier: 1, unitPrices: null, isDefault: false, weekdays: [1, 2, 3, 4, 5, 6, 7], windows: [{ uid: "w", start: "00:00", end: "24:00" }] },
      { uid: "b", phase: "OFF_PEAK", multiplier: 0.8, unitPrices: null, isDefault: true, weekdays: [], windows: [] }
    ]
  };
}

describe("validatePeakPricing 校验", () => {
  it("缺 pricing 报错", () => {
    expect(validatePeakPricing(null)).toHaveLength(1);
  });

  it("非法时区报错", () => {
    const issues = validatePeakPricing({ ...peakPricing(), timezone: "UTC+99" });
    expect(issues.some((i) => i.message.includes("时区"))).toBe(true);
  });

  it("首条必须是高峰，末条必须是兜底低峰", () => {
    const bad: PeakPricing = {
      timezone: "UTC+0",
      phases: [
        { uid: "a", phase: "OFF_PEAK", multiplier: 0.8, unitPrices: null, isDefault: true, weekdays: [], windows: [] },
        { uid: "b", phase: "PEAK", multiplier: 1, unitPrices: null, isDefault: false, weekdays: [1], windows: [{ uid: "w", start: "09:00", end: "18:00" }] }
      ]
    };
    const issues = validatePeakPricing(bad);
    expect(issues.some((i) => i.index === 0 && i.message.includes("首条"))).toBe(true);
    expect(issues.some((i) => i.index === 1 && i.message.includes("末条"))).toBe(true);
  });

  it("倍率必须大于 0 且不能大于 1（低峰是打折，不允许涨价）", () => {
    const bad: PeakPricing = {
      timezone: "UTC+0",
      phases: [
        { uid: "a", phase: "PEAK", multiplier: 0, unitPrices: null, isDefault: false, weekdays: [1], windows: [{ uid: "w", start: "09:00", end: "18:00" }] },
        { uid: "b", phase: "OFF_PEAK", multiplier: 1.5, unitPrices: null, isDefault: true, weekdays: [], windows: [] }
      ]
    };
    const issues = validatePeakPricing(bad);
    expect(issues.some((i) => i.message.includes("必须大于 0"))).toBe(true);
    expect(issues.some((i) => i.message.includes("不能大于 1"))).toBe(true);
  });

  it("合法配置无错误", () => {
    expect(validatePeakPricing(peakPricing())).toEqual([]);
  });
});

describe("resolvePeakPhase 本地相位判定", () => {
  it("命中高峰窗口返回 PEAK", () => {
    const decision = resolvePeakPhase(peakPricing(), "2026-10-19", "12:00");
    expect(decision?.phase).toBe("PEAK");
    expect(decision?.reason).toBe("PEAK_WINDOW");
  });

  it("未命中任何高峰走兜底低峰", () => {
    // 高峰项 weekdays 为空 -> 永不命中 -> 兜底 OFF_PEAK
    const pricing: PeakPricing = {
      timezone: "UTC+0",
      phases: [
        { uid: "a", phase: "PEAK", multiplier: 1, unitPrices: null, isDefault: false, weekdays: [], windows: [{ uid: "w", start: "09:00", end: "18:00" }] },
        { uid: "b", phase: "OFF_PEAK", multiplier: 0.8, unitPrices: null, isDefault: true, weekdays: [], windows: [] }
      ]
    };
    const decision = resolvePeakPhase(pricing, "2026-10-19", "12:00");
    expect(decision?.phase).toBe("OFF_PEAK");
    expect(decision?.reason).toBe("DEFAULT");
    expect(decision?.multiplier).toBe(0.8);
  });

  it("缺数据返回 null", () => {
    expect(resolvePeakPhase(null, "2026-10-19", "12:00")).toBeNull();
    expect(resolvePeakPhase(peakPricing(), "2026-10-19", "99:99")).toBeNull();
  });
});
