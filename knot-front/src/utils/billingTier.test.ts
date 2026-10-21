import { describe, it, expect } from "vitest";
import {
  toNumberOrNull,
  createTierPriceSet,
  validateTierRows,
  issuesOf,
  issueMessage,
  formatAmount,
  describeTierRange,
  tierScope,
  basePricesOf,
  type TierRow,
  type TierPriceSet
} from "@/utils/billingTier";

// p1-2 点名的高危交互之一：金额展示 + 计费规则正确性。
// 计费阶梯（TIERED）是计费模式里最容易算错的一段（区间不重叠、单价 6 项齐全、开放档位只能末档），
// 这些纯函数与后端 BillingConfig.validate 镜像，前端的校验缺失会直接把脏数据送后端 400。

function fullPrices(value = 1): TierPriceSet {
  return {
    input: value,
    output: value,
    cacheRead: value,
    cacheWrite: value,
    cacheWrite5m: value,
    cacheWrite1h: value
  };
}

function tierRow(from: number | null, to: number | null, prices: TierPriceSet): TierRow {
  return { uid: "t", from, to, unitPrices: prices };
}

describe("toNumberOrNull 宽松转数字（区分 0 与未填写）", () => {
  it("null / 空归一为 null，不与 0 混为一谈", () => {
    expect(toNumberOrNull(null)).toBeNull();
    expect(toNumberOrNull("")).toBeNull();
    expect(toNumberOrNull(undefined)).toBeNull();
  });

  it("数字串 / 数字 / 0 都正确转", () => {
    expect(toNumberOrNull("12")).toBe(12);
    expect(toNumberOrNull(12.5)).toBe(12.5);
    expect(toNumberOrNull(0)).toBe(0); // 0 是合法值，不能丢
  });

  it("非法值归一为 null", () => {
    expect(toNumberOrNull("abc")).toBeNull();
    expect(toNumberOrNull(NaN)).toBeNull();
  });
});

describe("formatAmount 千分位与占位符", () => {
  it("null 显示占位符", () => {
    expect(formatAmount(null)).toBe("—");
    expect(formatAmount("")).toBe("—");
  });

  it("千分位 + 保留小数（最多 6 位）", () => {
    expect(formatAmount(1000000)).toBe("1,000,000");
    expect(formatAmount(0)).toBe("0");
    expect(formatAmount(1234.5)).toBe("1,234.5");
    expect(formatAmount(1.123456789)).toBe("1.123457");
  });
});

describe("tierScope / basePricesOf 模式口径", () => {
  it("TOKEN 模式暴露 6 项基础价映射", () => {
    expect(tierScope("TOKEN").basePriceFields).not.toBeNull();
    const prices = basePricesOf(
      { inputUnitPrice: 1, outputUnitPrice: 2, cacheReadUnitPrice: 3, cacheWriteUnitPrice: 4, cacheWrite5mUnitPrice: 5, cacheWrite1hUnitPrice: 6 },
      "TOKEN"
    );
    expect(prices).toEqual<TierPriceSet>({ input: 1, output: 2, cacheRead: 3, cacheWrite: 4, cacheWrite5m: 5, cacheWrite1h: 6 });
  });

  it("非 TOKEN 模式无分项基础价，返回 null", () => {
    expect(tierScope("REQUEST").basePriceFields).toBeNull();
    expect(basePricesOf({ inputUnitPrice: 1 }, "REQUEST")).toBeNull();
  });
});

describe("createTierPriceSet 填充", () => {
  it("默认全 null，可填充基准值", () => {
    expect(createTierPriceSet()).toEqual<TierPriceSet>({ input: null, output: null, cacheRead: null, cacheWrite: null, cacheWrite5m: null, cacheWrite1h: null });
    const filled = createTierPriceSet(0);
    expect(filled.input).toBe(0);
    expect(filled.cacheWrite1h).toBe(0);
  });
});

describe("validateTierRows 阶梯校验", () => {
  it("至少 1 个档位", () => {
    const issues = validateTierRows([]);
    expect(issues).toHaveLength(1);
    expect(issues[0].index).toBe(-1);
  });

  it("合法单档（开放上不封顶）无错误", () => {
    expect(validateTierRows([tierRow(0, null, fullPrices())])).toEqual([]);
  });

  it("起始用量必填", () => {
    const issues = validateTierRows([tierRow(null, null, fullPrices())]);
    expect(issues.some((i) => i.field === "from" && i.message.includes("必填"))).toBe(true);
  });

  it("起始用量不能为负", () => {
    const issues = validateTierRows([tierRow(-1, null, fullPrices())]);
    expect(issues.some((i) => i.field === "from" && i.message.includes("不能为负"))).toBe(true);
  });

  it("起始不能大于截止", () => {
    const issues = validateTierRows([tierRow(100, 50, fullPrices())]);
    expect(issues.some((i) => i.field === "to" && i.message.includes("不能大于"))).toBe(true);
  });

  it("单价 6 项必须齐全", () => {
    const partial: TierPriceSet = { ...fullPrices(), output: null };
    const issues = validateTierRows([tierRow(0, null, partial)]);
    expect(issues.some((i) => i.field === "output" && i.message.includes("齐全"))).toBe(true);
  });

  it("单价不能为负", () => {
    const negative: TierPriceSet = { ...fullPrices(), input: -1 };
    const issues = validateTierRows([tierRow(0, null, negative)]);
    expect(issues.some((i) => i.field === "input" && i.message.includes("不能为负"))).toBe(true);
  });

  it("上一档未封顶则其后不能再配置档位", () => {
    const issues = validateTierRows([tierRow(0, null, fullPrices()), tierRow(100, null, fullPrices())]);
    expect(issues.some((i) => i.message.includes("上不封顶"))).toBe(true);
  });

  it("档位区间重叠报错", () => {
    const issues = validateTierRows([tierRow(0, 100, fullPrices()), tierRow(50, 200, fullPrices())]);
    expect(issues.some((i) => i.field === "from" && i.message.includes("大于上一档"))).toBe(true);
  });

  it("合法两档递增无错误（下一档起始严格大于上一档截止）", () => {
    expect(validateTierRows([tierRow(0, 100, fullPrices()), tierRow(101, null, fullPrices())])).toEqual([]);
  });
});

describe("issuesOf / issueMessage 取指定档位错误", () => {
  it("issuesOf 按 index 过滤", () => {
    // 第一档单价缺项（报错且 to=100 封顶），第二档合法 -> index1 无错
    const issues = validateTierRows([tierRow(0, 100, { ...fullPrices(), output: null }), tierRow(101, null, fullPrices())]);
    expect(issuesOf(issues, 0).length).toBeGreaterThan(0);
    expect(issuesOf(issues, 1)).toEqual([]);
  });

  it("issueMessage 取指定字段文案", () => {
    const issues = validateTierRows([tierRow(null, null, fullPrices())]);
    expect(issueMessage(issues, 0, "from")).toContain("必填");
    expect(issueMessage(issues, 0, "to")).toBe("");
  });
});

describe("describeTierRange 区间文案", () => {
  it("开放区间读「及以上」", () => {
    expect(describeTierRange(tierRow(0, null, fullPrices()))).toBe("0 及以上");
  });

  it("闭合区间", () => {
    expect(describeTierRange(tierRow(0, 100, fullPrices()))).toBe("0 ~ 100");
  });
});
