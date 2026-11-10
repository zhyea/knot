import {describe, expect, it} from "vitest";
import {
  appendResolutionRow,
  createResolutionRow,
  createUidGenerator
} from "@/utils/billingResolution";

describe("档位行工具", () => {
  it("createUidGenerator 前缀 + 自增序号，两次调用不重号", () => {
    const next = createUidGenerator("res");
    const a = next();
    const b = next();
    expect(a.startsWith("res-")).toBe(true);
    expect(b.startsWith("res-")).toBe(true);
    expect(a).not.toBe(b);
    expect(a.endsWith("-1")).toBe(true);
    expect(b.endsWith("-2")).toBe(true);
  });

  it("前缀支持 getter：props 变化后 uid 跟着变", () => {
    let prefix = "table-a";
    const next = createUidGenerator(() => prefix);
    expect(next().startsWith("table-a-")).toBe(true);
    prefix = "table-b";
    expect(next().startsWith("table-b-")).toBe(true);
  });

  it("appendResolutionRow 追加一行空档位并带上给定 uid", () => {
    const rows = [createResolutionRow("480P", "seed")];
    appendResolutionRow(rows, "new-uid");
    expect(rows).toHaveLength(2);
    expect(rows[1].uid).toBe("new-uid");
  });
});
