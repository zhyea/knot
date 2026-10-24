import {describe, expect, it} from "vitest";
import {normalizeProtocolCode} from "./protocolCode";

/**
 * 协议码基础归一（改进清单 3.10）：原先 `ModelFormDrawer` 与 `useDebugCapabilities`
 * 各有一份 trim+upper 实现，`RoutingRuleTestDrawer` 的同名函数则是 canonical 别名归一
 * 的薄包装——三份同名不同义。本 util 只承担基础归一，别名归一仍由
 * `useDebugCapabilities().canonicalOf` 负责（依赖后端能力接口）。
 */
describe("normalizeProtocolCode", () => {
  it("去首尾空白并转大写", () => {
    expect(normalizeProtocolCode("  chat_completions  ")).toBe("CHAT_COMPLETIONS");
    expect(normalizeProtocolCode("image_generations")).toBe("IMAGE_GENERATIONS");
  });

  it("null / undefined / 空白 → 空串", () => {
    expect(normalizeProtocolCode(null)).toBe("");
    expect(normalizeProtocolCode(undefined)).toBe("");
    expect(normalizeProtocolCode("")).toBe("");
    expect(normalizeProtocolCode("   ")).toBe("");
  });

  it("非字符串输入按 String() 语义（协议码来自表单与接口，类型不可信）", () => {
    // 与旧 `||` 版的唯一差异：0 / false 不再被当作空串吞掉。
    // 协议码是字符串枚举，实际不会传这两个值，选择语义更正确的 `??`。
    expect(normalizeProtocolCode(0)).toBe("0");
  });
});
