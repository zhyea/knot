import { describe, it, expect } from "vitest";
import { EMPTY_PLACEHOLDER, displayText, fmtMoney, roundPrice, formatDateTime } from "@/utils/format";
import { readFileSync, readdirSync } from "node:fs";
import { join, resolve } from "node:path";

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

describe("displayText 展示兜底", () => {
  it("null / undefined / 空白字符串显示统一占位符", () => {
    expect(displayText(null)).toBe("—");
    expect(displayText(undefined)).toBe("—");
    expect(displayText("")).toBe("—");
    expect(displayText("   ")).toBe("—");
    expect(displayText(null)).toBe(EMPTY_PLACEHOLDER);
  });

  it("空串以外的文本原样返回，不裁剪内容", () => {
    expect(displayText("gpt-4o")).toBe("gpt-4o");
    expect(displayText(" 已停用 ")).toBe(" 已停用 ");
  });

  it("⭐ 数字 0 与 false 照常显示，不被当成空值", () => {
    // 本项目「状态 0 是合法值」是头号展示风险：`value || "-"` 会把 0=停用
    // 与「未填写」显示成同一个占位符，运维无法区分。
    expect(displayText(0)).toBe("0");
    expect(displayText(false)).toBe("false");
    expect(displayText(0)).not.toBe(EMPTY_PLACEHOLDER);
  });

  it("可自定义占位符", () => {
    expect(displayText(null, "-")).toBe("-");
    expect(displayText("", "N/A")).toBe("N/A");
  });
});

/**
 * 2026-11-11 第六批：DepartmentListPanel / UserListPanel 曾各自本地定义一个**同名**
 * formatDateTime（new Date(...).toLocaleString("zh-CN", {...})），遮蔽了全站唯一出口。
 *
 * 判据不是「同名就合并」，而是：后端 LocalDateTime 由 Spring MVC ObjectMapper 序列化为
 * ISO 字符串，全局实现是确定性的字符串处理且兼容 Jackson LocalDateTime 数组；本地实现
 * 依赖 ICU（分隔符 / hour12），遇到数组形态还会输出 Invalid Date。16 个文件已走全局，
 * 只剩 2 个副本，属「唯一出口漏网」而非业务编排差异。
 */
const srcRoot = resolve(process.cwd(), "src");

function collectVue(dir: string, acc: string[] = []): string[] {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) {
      collectVue(path, acc);
    } else if (entry.name.endsWith(".vue")) {
      acc.push(path);
    }
  }
  return acc;
}

describe("formatDateTime 唯一出口（防本地同名副本回潮）", () => {
  it("ISO 字符串 → 确定性 YYYY-MM-DD HH:mm:ss，不依赖 toLocaleString", () => {
    expect(formatDateTime("2026-11-10T23:36:27")).toBe("2026-11-10 23:36:27");
    expect(formatDateTime("2026-11-10T23:36:27.123456")).toBe("2026-11-10 23:36:27");
  });

  it("Jackson LocalDateTime 数组 → 同样格式（旧本地 new Date 会 Invalid Date）", () => {
    expect(formatDateTime([2026, 11, 10, 23, 36, 27])).toBe("2026-11-10 23:36:27");
    expect(formatDateTime([2026, 11, 10, 23, 36, 27, 123456789])).toBe("2026-11-10 23:36:27");
  });

  it("空值走统一占位符，非时间文本原样回退", () => {
    expect(formatDateTime(null)).toBe(EMPTY_PLACEHOLDER);
    expect(formatDateTime(undefined)).toBe(EMPTY_PLACEHOLDER);
    expect(formatDateTime("")).toBe(EMPTY_PLACEHOLDER);
    expect(formatDateTime("待补充")).toBe("待补充");
  });

  it("扫描到足够多的 .vue（防止源码级断言空转）", () => {
    expect(collectVue(srcRoot).length).toBeGreaterThan(100);
  });

  it("组件里不得再本地定义 formatDateTime", () => {
    const hits = collectVue(srcRoot).filter((path) =>
      /function formatDateTime\(|const formatDateTime/.test(readFileSync(path, "utf8"))
    );
    expect(hits).toEqual([]);
  });

  it("两个系统列表组件从唯一出口 import，且不再用 toLocaleString", () => {
    for (const rel of ["components/system/DepartmentListPanel.vue", "components/system/UserListPanel.vue"]) {
      const text = readFileSync(resolve(srcRoot, rel), "utf8");
      expect(text).toMatch(/import \{[^}]*formatDateTime[^}]*\} from "@\/utils\/format"/);
      expect(text).not.toContain("toLocaleString");
    }
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
