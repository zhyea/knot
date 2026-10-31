import {describe, it, expect} from "vitest";
import {buildActivityMap, buildMetrics, normalizeTotal} from "@/utils/dashboardMetrics";
import type {DashboardTotals} from "@/utils/dashboardMetrics";

/**
 * 指标卡的**比例口径**是这一页最容易悄悄错的地方：
 * 分母兜底、两处下限（比例 8 / 柱高 22）都是「看起来没坏但数字在撒谎」的类型，
 * 所以全部用纯函数钉住，不依赖挂载。
 */
function totals(patch: Partial<DashboardTotals> = {}): DashboardTotals {
  return {apps: 0, providers: 0, logicalModels: 0, routingRules: 0, ...patch};
}

describe("normalizeTotal", () => {
  it("三种返回形态都认：数组 / 标准分页 total / 只有 list", () => {
    expect(normalizeTotal([1, 2, 3])).toBe(3);
    expect(normalizeTotal({total: 42, list: []})).toBe(42);
    expect(normalizeTotal({list: [{}, {}]})).toBe(2);
    // 字符串数字也认（后端分页字段类型不统一）
    expect(normalizeTotal({total: "7"})).toBe(7);
  });

  it("认不出时返回 0，不猜", () => {
    expect(normalizeTotal(null)).toBe(0);
    expect(normalizeTotal(undefined)).toBe(0);
    expect(normalizeTotal({})).toBe(0);
    // 负数不是合法总数 → 落回 list 分支 → 0
    expect(normalizeTotal({total: -1})).toBe(0);
    expect(normalizeTotal("oops")).toBe(0);
  });
});

describe("buildActivityMap", () => {
  it("按模块累计，跳过没有 module 的记录", () => {
    const map = buildActivityMap([
      {module: "app", operation: "a"},
      {module: "app", operation: "b"},
      {module: "routing", operation: "c"},
      {module: "", operation: "d"},
      {operation: "e"}
    ]);
    expect(map).toEqual({app: 2, routing: 1});
  });
});

describe("buildMetrics", () => {
  it("四项顺序与 key 固定：卡片顺序变了整个看板就错位", () => {
    const metrics = buildMetrics(totals(), {});
    expect(metrics.map((item) => item.key)).toEqual(["apps", "providers", "logicalModels", "routingRules"]);
    expect(metrics.map((item) => item.label)).toEqual(["应用", "供应商账户", "统一模型", "路由规则"]);
    expect(metrics.map((item) => item.tone)).toEqual(["blue", "teal", "green", "orange"]);
  });

  it("全 0 也不除零：比例走 8% 下限，柱高走 22 下限", () => {
    const metrics = buildMetrics(totals(), {});
    expect(metrics.every((item) => item.value === 0)).toBe(true);
    expect(metrics.map((item) => item.ratio)).toEqual([8, 8, 8, 8]);
    expect(metrics.every((item) => item.distribution.every((bar) => bar.height === 22))).toBe(true);
  });

  it("比例按最大值归一，0 值项仍留在下限上", () => {
    const metrics = buildMetrics(totals({apps: 10}), {});
    expect(metrics[0].ratio).toBe(100);
    expect(metrics[0].distribution[0].height).toBe(100);
    // 其余三项值为 0 → 落在下限
    expect(metrics.slice(1).map((item) => item.ratio)).toEqual([8, 8, 8]);
    expect(metrics[1].distribution[1].height).toBe(22);
  });

  it("每张卡的柱组只有自己那根高亮，且柱组恒为 4 根", () => {
    const metrics = buildMetrics(totals({apps: 5, providers: 5}), {});
    for (const [index, item] of metrics.entries()) {
      expect(item.distribution).toHaveLength(4);
      const actives = item.distribution.map((bar, i) => (bar.active ? i : -1)).filter((i) => i >= 0);
      expect(actives).toEqual([index]);
    }
  });

  it("活动文案按 moduleKey 取数：命中写条数，未命中写「最近无相关操作」", () => {
    const metrics = buildMetrics(totals({apps: 3}), {app: 2, "provider-account": 0});
    expect(metrics[0].activityCount).toBe(2);
    expect(metrics[0].helperText).toBe("最近 2 条相关操作");
    // activityCount 为 0 与「没有这个 key」必须走同一条文案
    expect(metrics[1].activityCount).toBe(0);
    expect(metrics[1].helperText).toBe("最近无相关操作");
    expect(metrics[2].helperText).toBe("最近无相关操作");
  });
});
