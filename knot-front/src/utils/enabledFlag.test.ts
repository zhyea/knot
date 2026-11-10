import {describe, expect, it} from "vitest";
import {readFileSync, readdirSync} from "node:fs";
import {join, resolve} from "node:path";
import {isRowEnabled, normalizeEnabledFlag} from "./enabledFlag";
import {normalizeRetryPolicy} from "./retryPolicy";
import {normalizeEjectionPolicy} from "./ejectionPolicy";
import {normalizeStickyPolicy} from "./stickyPolicy";

/**
 * 启用态口径守门（2026-11-10 第五批）。
 *
 * <p>只统一了两类**同形态**的事实：el-switch 入参归一、列表行 primitive boolean 读取。
 * 另外三类是真实差异，本测试把它们钉死，防止后来者「为了统一而统一」：</p>
 * 1. 配置 JSON 的 enabled 缺省默认值：重试默认开、熔断/粘性默认关；
 * 2. 数字状态列 status（0 是合法停用值），走 EnabledStatus 常量比较；
 * 3. 表单回填的严格 `=== true` / `!!` / `!== false` 各随实体默认值。
 */

const src = resolve(process.cwd(), "src");

function collect(dir: string, acc: string[] = []): string[] {
  for (const entry of readdirSync(dir, {withFileTypes: true})) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) {
      collect(path, acc);
    } else if (/\.(vue|ts)$/.test(entry.name)
      && !entry.name.endsWith(".test.ts")
      && !entry.name.endsWith(".d.ts")) {
      acc.push(path);
    }
  }
  return acc;
}

const sources = collect(src).map((path) => [path, readFileSync(path, "utf8")] as const);
const inlineFlag = 'const flag = enabled !== false && enabled !== 0 && enabled !== "false";';
const inlineRow = ':model-value="row.enabled !== false"';

describe("normalizeEnabledFlag：el-switch 入参归一", () => {
  it("false / 0 / \"false\" 都归一为禁用（不能 truthy 判断）", () => {
    expect(normalizeEnabledFlag(false)).toBe(false);
    expect(normalizeEnabledFlag(0)).toBe(false);
    expect(normalizeEnabledFlag("false")).toBe(false);
    // 反例：字符串 "false" 用 !! 会被判成 true，正是不能写 truthy 的原因
    expect(!!"false").toBe(true);
  });

  it("其余入参归一为启用，与旧内联实现逐值等价", () => {
    for (const value of [true, 1, "true", "", "1"] as const) {
      expect(normalizeEnabledFlag(value)).toBe(true);
    }
  });
});

describe("isRowEnabled：列表行 primitive boolean 契约", () => {
  it("缺省 / null 视为启用（刻意不用 === true）", () => {
    expect(isRowEnabled({})).toBe(true);
    expect(isRowEnabled(null)).toBe(true);
    expect(isRowEnabled(undefined)).toBe(true);
  });

  it("显式 false 才是禁用", () => {
    expect(isRowEnabled({enabled: false})).toBe(false);
    expect(isRowEnabled({enabled: true})).toBe(true);
  });

  it("数值 0 不参与本口径：它属于数字 status 列，另有 EnabledStatus 比较", () => {
    // 保持与旧 `row.enabled !== false` 一致；若后端误下发 status 0，
    // LogicalModelList.test.ts 的 boolean 契约断言会变红提示回归。
    expect(isRowEnabled({enabled: 0})).toBe(true);
  });
});

describe("刻意不统一的三类口径", () => {
  it("配置 JSON 缺省默认值：重试开 / 熔断关 / 粘性关", () => {
    expect(normalizeRetryPolicy({}).enabled).toBe(true);
    expect(normalizeEjectionPolicy({}).enabled).toBe(false);
    expect(normalizeStickyPolicy({}).enabled).toBe(false);
  });

  it("数字 status 列仍走 EnabledStatus 常量与 status === 0 判据", () => {
    const presetView = readFileSync(resolve(src, "views/routing/TestRequestPresetView.vue"), "utf8");
    const presetForm = readFileSync(resolve(src, "components/routing/TestRequestPresetFormDrawer.vue"), "utf8");
    expect(presetView).toContain("prevStatus === EnabledStatus.ENABLED");
    expect(presetForm).toContain("row?.status === 0");
  });

  it("表单回填的严格 / 宽松口径仍在（各随实体默认值，不并入唯一出口）", () => {
    const modelForm = readFileSync(resolve(src, "components/model/ModelFormDrawer.vue"), "utf8");
    const accountForm = readFileSync(resolve(src, "components/provider/ProviderAccountFormDrawer.vue"), "utf8");
    const familyForm = readFileSync(resolve(src, "components/model/ModelFamilyFormDialog.vue"), "utf8");
    expect(modelForm).toContain("row.enabled === true");
    expect(accountForm).toContain("!!row.enabled");
    expect(familyForm).toContain("props.family.enabled !== false");
  });
});

describe("唯一出口：源码里不再有散落的内联实现", () => {
  it("扫描到足够的源码文件（防止断言因匹配失效而空转）", () => {
    expect(sources.length).toBeGreaterThan(100);
  });

  it("不再有任何文件内联开关入参归一", () => {
    const hits = sources.filter(([, text]) => text.includes(inlineFlag)).map(([path]) => path);
    expect(hits).toEqual([]);
  });

  it("列表模板不再直接写 row.enabled !== false", () => {
    const hits = sources.filter(([, text]) => text.includes(inlineRow)).map(([path]) => path);
    expect(hits).toEqual([]);
  });

  it("两个函数只在 enabledFlag.ts 定义", () => {
    const defs = sources.filter(([, text]) => text.includes("export function normalizeEnabledFlag("));
    expect(defs.map(([path]) => path)).toEqual([resolve(src, "utils/enabledFlag.ts")]);
  });
});
