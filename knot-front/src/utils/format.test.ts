import { describe, it, expect } from "vitest";
import { fmtMoney, roundPrice } from "@/utils/format";

// p1-2 点名的高危交互之一：金额展示。折扣精度（P3-1）与计费单价常在 1e-6 ~ 1e-8，
// 展示若只用 4 位会把 0.00000001 显示成 0.0000，看起来像「没花钱」。这里锁住精度与边界。

describe("fmtMoney 金额展示", () => {
  it("null / 空值显示占位符 —", () => {
    expect(fmtMoney(null)).toBe("—");
    expect(fmtMoney(undefined)).toBe("—");
    expect(fmtMoney("")).toBe("—");
  });

  it("非数字原样回退，不崩", () => {
    expect(fmtMoney("abc")).toBe("abc");
    expect(fmtMoney(NaN)).toBe("NaN");
  });

  it("按 8 位小数定点展示，吞掉 1e-8 这类小单价", () => {
    expect(fmtMoney(0.00000001)).toBe("0.00000001");
    expect(fmtMoney(1e-6)).toBe("0.00000100");
    expect(fmtMoney(0.0125)).toBe("0.01250000");
    expect(fmtMoney(1234.5)).toBe("1234.50000000");
  });

  it("负数同样保留 8 位", () => {
    expect(fmtMoney(-0.00000001)).toBe("-0.00000001");
  });
});

describe("roundPrice 精度收敛", () => {
  it("空/非法值返回 undefined（便于回写可空字段）", () => {
    expect(roundPrice(null)).toBeUndefined();
    expect(roundPrice("")).toBeUndefined();
    expect(roundPrice("abc")).toBeUndefined();
  });

  it("默认 8 位四舍五入，不补零", () => {
    expect(roundPrice(0.00000001)).toBe(0.00000001);
    expect(roundPrice(0.5)).toBe(0.5);
    expect(roundPrice(1234.567891234)).toBe(1234.56789123);
  });

  it("指定小数位按位收敛", () => {
    expect(roundPrice(0.123456789, 4)).toBe(0.1235);
    expect(roundPrice(1.005, 2)).toBe(1.01);
  });

  it("负数四舍五入", () => {
    expect(roundPrice(-0.000000019)).toBe(-0.00000002);
  });
});
