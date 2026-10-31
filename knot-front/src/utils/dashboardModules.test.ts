import {describe, it, expect} from "vitest";
import {
  buildModuleNameMap,
  FALLBACK_MODULE_NAMES,
  MODULE_LIMIT,
  rankModules,
  resolveModuleName
} from "@/utils/dashboardModules";
import type {DashboardModuleItem} from "@/utils/dashboardModules";

function module(code: string, capabilityCount: number): DashboardModuleItem {
  return {
    code,
    name: `${code}-name`,
    capabilities: Array.from({length: capabilityCount}, (_, i) => `${code}-cap-${i}`)
  };
}

describe("buildModuleNameMap", () => {
  it("code → name；空目录得到空表而不是抛错", () => {
    expect(buildModuleNameMap([module("app", 1), module("routing", 0)])).toEqual({
      app: "app-name",
      routing: "routing-name"
    });
    expect(buildModuleNameMap([])).toEqual({});
  });
});

describe("rankModules", () => {
  it("按能力数降序，并截到前 6 个", () => {
    const catalog = Array.from({length: 8}, (_, i) => module(`m${i}`, i + 1));
    const ranked = rankModules(catalog);
    expect(ranked).toHaveLength(MODULE_LIMIT);
    expect(ranked.map((item) => item.code)).toEqual(["m7", "m6", "m5", "m4", "m3", "m2"]);
    // 入参不被改写（内部先展开）
    expect(catalog[0].code).toBe("m0");
  });

  it("能力预览取前 3 个，剩下的折成 +N", () => {
    const [ranked] = rankModules([module("app", 5)]);
    expect(ranked.capabilityCount).toBe(5);
    expect(ranked.capabilityPreview).toEqual(["app-cap-0", "app-cap-1", "app-cap-2"]);
    expect(ranked.remainingCapabilityCount).toBe(2);
  });

  it("能力不足 3 个时不出现负数", () => {
    const [ranked] = rankModules([module("app", 1)]);
    expect(ranked.remainingCapabilityCount).toBe(0);
    expect(ranked.capabilityPreview).toEqual(["app-cap-0"]);
  });

  it("capabilities 缺省 / 非数组一律按 0 项处理", () => {
    const ranked = rankModules([{code: "app", name: "应用"}, {code: "x", name: "x", capabilities: undefined}]);
    expect(ranked.map((item) => item.capabilityCount)).toEqual([0, 0]);
    expect(ranked[0].capabilityPreview).toEqual([]);
    expect(ranked[0].remainingCapabilityCount).toBe(0);
  });
});

describe("resolveModuleName", () => {
  it("三级兜底：接口下发名 → 内置兜底表 → 原样 code", () => {
    const nameMap = buildModuleNameMap([{code: "app", name: "应用管理（新）"}]);
    expect(resolveModuleName("app", nameMap)).toBe("应用管理（新）");
    // 接口没给 → 走内置表
    expect(resolveModuleName("logical-model", {})).toBe(FALLBACK_MODULE_NAMES["logical-model"]);
    // 两边都没有 → 回显 code，而不是空字符串
    expect(resolveModuleName("brand-new-module", {})).toBe("brand-new-module");
  });

  it("code 为空时给占位符，不返回空白", () => {
    expect(resolveModuleName("", {})).toBe("—");
    expect(resolveModuleName(undefined as unknown as string, {})).toBe("—");
  });
});
