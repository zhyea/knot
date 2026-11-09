import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

/**
 * 供应商信息列表「右侧固定操作栏」契约守门。
 *
 * el-table 的 fixed 是布局期行为（EP 2.9 用 sticky 单元格实现），jsdom 没有布局，
 * 无法可靠断言运行时是否贴右；因此直接钉死模板契约：操作列必须是最后一列、
 * fixed="right"、固定宽度，并带 row-action-column 钩子（全局样式靠它给固定栏
 * 常驻左分隔与阴影）。删掉任意一个都会让「操作栏固定」退化成普通末列。
 */
const source = readFileSync(
  fileURLToPath(new URL("./ProviderProfileListPanel.vue", import.meta.url)),
  "utf8"
);
const columns = [...source.matchAll(/<el-table-column\b[^>]*>/g)].map((match) => match[0]);
const actionColumn = columns[columns.length - 1] ?? "";

describe("供应商信息列表右侧固定操作栏", () => {
  it("操作列是最后一列且固定在右侧", () => {
    expect(actionColumn).toContain('label="操作"');
    expect(actionColumn).toContain('fixed="right"');
  });

  it("操作列固定宽度并带固定栏样式钩子", () => {
    expect(actionColumn).toMatch(/width="\d+"/);
    expect(actionColumn).toContain('class-name="row-action-column"');
    expect(actionColumn).toContain('label-class-name="row-action-column"');
  });

  it("全表有且仅有一列 fixed=right", () => {
    expect(columns.filter((column) => column.includes('fixed="right"'))).toHaveLength(1);
    expect(columns.length).toBeGreaterThan(1);
  });
});
