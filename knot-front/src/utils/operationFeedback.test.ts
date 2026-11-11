import {describe, expect, it} from "vitest";
import {readFileSync, readdirSync} from "node:fs";
import {join, resolve} from "node:path";

/**
 * 操作反馈文案口径守门（2026-11-11 第七批）。
 *
 * <p>同一操作曾有两套成功文案：保存 `已保存`(16 文件) / `保存成功`(4 文件)，
 * 删除 `已删除`(10 文件) / `删除成功`(6 处)。用户会把两种措辞误读成不同的结果状态，
 * 而这类差异不会让任何类型检查或组件测试变红。口径按**实测多数派**统一，
 * 且 `已保存` 已被 BillingRuleFormDialog.test.ts / RoutingRuleFormDrawer.test.ts 断言背书。</p>
 *
 * <p>⚠ 失败态 `ElMessage.error("删除失败")` 是另一类结果，刻意保留，不随成功态一起改。</p>
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

function hits(needle: string): string[] {
  return sources.filter(([, text]) => text.includes(needle)).map(([path]) => path);
}

const MINORITY_SAVE = 'ElMessage.success("保存成功")';
const MINORITY_DELETE = 'ElMessage.success("删除成功")';
const CANONICAL_SAVE = 'ElMessage.success("已保存")';
const CANONICAL_DELETE = 'ElMessage.success("已删除")';
const FAILURE_DELETE = 'ElMessage.error("删除失败")';

describe("操作反馈文案唯一口径", () => {
  it("扫描到足够源码（防止源码级断言空转）", () => {
    expect(sources.length).toBeGreaterThan(100);
  });

  it("保存成功态不再出现「保存成功」", () => {
    expect(hits(MINORITY_SAVE)).toEqual([]);
  });

  it("删除成功态不再出现「删除成功」", () => {
    expect(hits(MINORITY_DELETE)).toEqual([]);
  });

  it("多数派口径仍在使用（防止把两侧一起删掉造成假绿）", () => {
    expect(sources.reduce((n, [, text]) => n + text.split(CANONICAL_SAVE).length - 1, 0)).toBeGreaterThanOrEqual(16);
    expect(sources.reduce((n, [, text]) => n + text.split(CANONICAL_DELETE).length - 1, 0)).toBeGreaterThanOrEqual(16);
    expect(hits(CANONICAL_SAVE).length).toBeGreaterThanOrEqual(10);
    expect(hits(CANONICAL_DELETE).length).toBeGreaterThanOrEqual(10);
  });

  it("失败态「删除失败」保留 4 处，不随成功态一起被改写", () => {
    expect(sources.reduce((n, [, text]) => n + text.split(FAILURE_DELETE).length - 1, 0)).toBe(4);
  });
});
