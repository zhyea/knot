import {beforeEach, describe, expect, it, vi} from "vitest";

// labelOf 是模块级只读缓存，/api/enum-options 的加载只由 useEnumOptions() 触发。
// 这里复刻真实 labelOf 语义（空值走 fallback、未命中走 fallback 或回显 code），
// 并把它做成 spy，用来钉死「展示函数工厂必须经 hook 取 labelOf」这条加载链。
const {labelOf, useEnumOptions} = vi.hoisted(() => {
  const labelOf = vi.fn((enumName: string, code: unknown, fallback?: string) => {
    if (code === null || code === undefined || code === "") return fallback ?? "—";
    return fallback || `${enumName}:${String(code)}`;
  });
  const useEnumOptions = vi.fn(() => ({labelOf}));
  return {labelOf, useEnumOptions};
});

vi.mock("@/composables/useEnumOptions", () => ({useEnumOptions}));

import {useEnumText, useModelTypeText} from "@/composables/useEnumText";
import {EMPTY_PLACEHOLDER} from "@/utils/format";

beforeEach(() => {
  labelOf.mockClear();
  useEnumOptions.mockClear();
});

describe("useEnumText 加载链与透传", () => {
  it("必须经 useEnumOptions() 取 labelOf，否则枚举不加载、状态列退化成落库码", () => {
    const modeLabel = useEnumText("BillingModeEnum");
    expect(useEnumOptions).toHaveBeenCalledTimes(1);
    expect(modeLabel("TOKEN")).toBe("BillingModeEnum:TOKEN");
    expect(labelOf).toHaveBeenCalledWith("BillingModeEnum", "TOKEN", undefined);
  });

  it("枚举键按工厂参数传递，不同工厂互不串台", () => {
    const modeLabel = useEnumText("BillingModeEnum");
    const planLabel = useEnumText("PricingPlanEnum");
    expect(modeLabel("X")).toBe("BillingModeEnum:X");
    expect(planLabel("X")).toBe("PricingPlanEnum:X");
  });

  it("fallback 按 code 计算后透传给 labelOf，不自建词表", () => {
    const label = useEnumText("PricingPlanEnum", {fallback: (code) => `未配置:${String(code)}`});
    expect(label("UNKNOWN")).toBe("未配置:UNKNOWN");
    expect(labelOf).toHaveBeenCalledWith("PricingPlanEnum", "UNKNOWN", "未配置:UNKNOWN");
  });

  it("labelOf 命中时直接返回它的结果，工厂不劫持展示值", () => {
    labelOf.mockReturnValueOnce("按量计费");
    const modeLabel = useEnumText("BillingModeEnum");
    expect(modeLabel("TOKEN")).toBe("按量计费");
  });
});

describe("useEnumText 空值口径", () => {
  it("声明 emptyText 后对 null / undefined / 空串短路，且不查枚举", () => {
    const label = useEnumText("ModelTypeEnum", {emptyText: EMPTY_PLACEHOLDER});
    expect(label(null)).toBe(EMPTY_PLACEHOLDER);
    expect(label(undefined)).toBe(EMPTY_PLACEHOLDER);
    expect(label("")).toBe(EMPTY_PLACEHOLDER);
    expect(labelOf).not.toHaveBeenCalled();
  });

  it("数字 0 是合法枚举 code，不得被 truthy 判成空值", () => {
    const label = useEnumText("AsyncTaskStatusEnum", {emptyText: EMPTY_PLACEHOLDER});
    expect(label(0)).toBe("AsyncTaskStatusEnum:0");
    expect(labelOf).toHaveBeenCalledWith("AsyncTaskStatusEnum", 0, undefined);
  });

  it("未声明 emptyText 时把空值交给 labelOf 处理（保留原组件语义）", () => {
    const label = useEnumText("OperationLogStatusEnum");
    expect(label(null)).toBe("—");
    expect(labelOf).toHaveBeenCalledWith("OperationLogStatusEnum", null, undefined);
  });
});

describe("useModelTypeText", () => {
  it("绑定 ModelTypeEnum：空值走全站唯一占位，未命中回显 code", () => {
    const label = useModelTypeText();
    expect(label("")).toBe(EMPTY_PLACEHOLDER);
    expect(label("CHAT")).toBe("CHAT");
    expect(labelOf).toHaveBeenCalledWith("ModelTypeEnum", "CHAT", "CHAT");
  });

  it("0 也不被占位吞掉（与 useEnumText 同一口径）", () => {
    const label = useModelTypeText();
    expect(label(0)).toBe("0");
    expect(labelOf).toHaveBeenCalledWith("ModelTypeEnum", 0, "0");
  });
});
