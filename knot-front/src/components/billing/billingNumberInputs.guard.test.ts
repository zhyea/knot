import {describe, expect, it} from "vitest";
import {readFileSync, readdirSync} from "node:fs";
import {join} from "node:path";
import {fileURLToPath} from "node:url";

/**
 * 计费规则抽屉的数字输入框守门：控件位置与「计数类字段禁小数」两条口径都落在模板属性上，
 * 组件挂载测试只能覆盖当前渲染出来的模式/方案，改到别的分支就静默放过。
 *
 * <p>这里直接读 SFC 源码逐块扫描 `<el-input-number>`，任何新增/改动的输入框都逃不过，
 * 覆盖全部计费模式与进阶方案。</p>
 */

const BILLING_DIR = fileURLToPath(new URL("./", import.meta.url));

/** 计数类字段：张数、秒数、token 数、像素数都是不可拆分的整数，必须声明 precision=0 */
const COUNT_FIELDS = [
  "row.from",
  "row.to",
  "row.maxPixels",
  "form.imageFreeInputImages",
  "form.videoFreeInputImages",
  "usageAmount"
];

function vueFiles(dir: string): string[] {
  return readdirSync(dir, {withFileTypes: true}).flatMap((entry) => {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      return vueFiles(full);
    }
    return entry.isFile() && entry.name.endsWith(".vue") ? [full] : [];
  });
}

/** 从 `<el-input-number` 到闭合 `/>` 的整块属性文本；模板里这些标签一律自闭合 */
function inputNumberBlocks(source: string): string[] {
  return [...source.matchAll(/<el-input-number\b([\s\S]*?)\/>/g)].map((match) => match[1]);
}

describe("计费规则数字输入框守门", () => {
  const files = vueFiles(BILLING_DIR);
  const blocks = files.flatMap((file) =>
    inputNumberBlocks(readFileSync(file, "utf8")).map((block) => ({
      file: file.slice(BILLING_DIR.length).replace(/\\/g, "/"),
      block
    }))
  );

  it("扫描到全部计费输入框（防止正则失效后静默全绿）", () => {
    expect(blocks.length).toBeGreaterThan(20);
  });

  it("加减控件一律在右侧", () => {
    const offenders = blocks
      .filter(({block}) => !block.includes('controls-position="right"'))
      .map(({file, block}) => `${file}: ${block.trim().split("\n")[0]}`);
    expect(offenders).toEqual([]);
  });

  it("计数类字段禁小数", () => {
    const offenders = blocks
      .filter(({block}) => COUNT_FIELDS.some((field) => block.includes(`"${field}"`)))
      .filter(({block}) => !block.includes(':precision="0"'))
      .map(({file, block}) => `${file}: ${block.trim().split("\n")[0]}`);
    expect(offenders).toEqual([]);
  });
});
