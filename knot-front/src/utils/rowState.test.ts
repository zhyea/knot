import {describe, it, expect} from "vitest";
import type {Row} from "@/types";
import {isDeletedRow, deletedRowClassName} from "@/utils/rowState";

function row(deleted: unknown): Row {
  return {id: 1, deleted} as Row;
}

describe("isDeletedRow 严格判 true（禁 truthy）", () => {
  it("boolean true 才算已删除", () => {
    expect(isDeletedRow(row(true))).toBe(true);
    expect(isDeletedRow(row(false))).toBe(false);
  });

  it("数字 1 / 0、字符串 'true'、null、undefined 都不是删除态", () => {
    for (const value of [1, 0, "true", "", null, undefined]) {
      expect(isDeletedRow(row(value))).toBe(false);
    }
  });

  it("字段缺失同样不是删除态", () => {
    expect(isDeletedRow({id: 1} as Row)).toBe(false);
  });
});

describe("deletedRowClassName", () => {
  it("只有严格 true 返回删除态样式", () => {
    expect(deletedRowClassName({row: row(true)})).toBe("row-deleted");
    expect(deletedRowClassName({row: row(1)})).toBe("");
    expect(deletedRowClassName({row: row(null)})).toBe("");
  });
});
